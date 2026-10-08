/* A small Markdown renderer for story replies. Text is always inserted through textContent. */
window.RichMessage = (() => {
  const heading = /^\s*(#{1,4})\s+(.+)$/;
  const bullet = /^\s*[-*+]\s+(.+)$/;
  const numbered = /^\s*\d+[.)]\s+(.+)$/;
  const rule = /^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/;
  const detailStart = /^\s*:::details\s+(.+)\s*$/i;
  const htmlDetailStart = /^\s*<details>\s*$/i;
  const htmlDetailInline = /^\s*<details>\s*<summary>(.*?)<\/summary>\s*$/i;
  const htmlSummary = /^\s*<summary>(.*?)<\/summary>\s*$/i;
  const inline = /(\*\*[^*\n]+\*\*|__[^_\n]+__|`[^`\n]+`|\*[^*\n]+\*|_[^_\n]+_)/g;

  function appendInline(root, text) {
    let from = 0;
    for (const match of text.matchAll(inline)) {
      if (match.index > from) root.append(document.createTextNode(text.slice(from, match.index)));
      const token = match[0];
      const bold = token.startsWith('**') || token.startsWith('__');
      const code = token.startsWith('`');
      const node = document.createElement(code ? 'code' : bold ? 'strong' : 'em');
      node.textContent = token.slice(bold ? 2 : 1, bold ? -2 : -1);
      root.append(node); from = match.index + token.length;
    }
    if (from < text.length) root.append(document.createTextNode(text.slice(from)));
  }

  function startsBlock(line) {
    return heading.test(line) || bullet.test(line) || numbered.test(line) || rule.test(line)
      || detailStart.test(line) || htmlDetailStart.test(line) || htmlDetailInline.test(line) || /^\s*>/.test(line) || /^\s*```/.test(line);
  }

  function renderLines(lines, root) {
    for (let i = 0; i < lines.length;) {
      const line = lines[i];
      if (!line.trim()) { i++; continue; }

      const customDetail = line.match(detailStart);
      const inlineDetail = line.match(htmlDetailInline);
      const htmlDetail = htmlDetailStart.test(line) || Boolean(inlineDetail);
      if (customDetail || htmlDetail) {
        let title = customDetail ? customDetail[1] : inlineDetail ? inlineDetail[1] : '展开资料';
        if (htmlDetail && !inlineDetail && htmlSummary.test(lines[i + 1] || '')) { title = lines[i + 1].match(htmlSummary)[1]; i++; }
        const start = ++i;
        while (i < lines.length && !(customDetail ? /^\s*:::\s*$/.test(lines[i]) : /^\s*<\/details>\s*$/i.test(lines[i]))) i++;
        const details = document.createElement('details'); details.className = 'story-details';
        const summary = document.createElement('summary'); appendInline(summary, title); details.append(summary);
        const inside = document.createElement('div'); inside.className = 'story-details-content';
        renderLines(lines.slice(start, i), inside); details.append(inside); root.append(details);
        if (i < lines.length) i++;
        continue;
      }

      const title = line.match(heading);
      if (title) {
        const node = document.createElement(`h${Math.min(title[1].length + 2, 6)}`);
        appendInline(node, title[2]); root.append(node); i++; continue;
      }
      if (rule.test(line)) { root.append(document.createElement('hr')); i++; continue; }

      const list = line.match(bullet) || line.match(numbered);
      if (list) {
        const ordered = numbered.test(line);
        const node = document.createElement(ordered ? 'ol' : 'ul');
        while (i < lines.length) {
          const next = lines[i].match(ordered ? numbered : bullet);
          if (!next) break;
          const item = document.createElement('li'); appendInline(item, next[1]); node.append(item); i++;
        }
        root.append(node); continue;
      }

      if (/^\s*>/.test(line)) {
        const quote = document.createElement('blockquote'); const parts = [];
        while (i < lines.length && /^\s*>/.test(lines[i])) parts.push(lines[i++].replace(/^\s*>\s?/, ''));
        appendInline(quote, parts.join('\n')); root.append(quote); continue;
      }
      if (/^\s*```/.test(line)) {
        const code = document.createElement('code'), pre = document.createElement('pre'); i++;
        const parts = []; while (i < lines.length && !/^\s*```/.test(lines[i])) parts.push(lines[i++]);
        code.textContent = parts.join('\n'); pre.append(code); root.append(pre);
        if (i < lines.length) i++;
        continue;
      }

      const parts = [line]; i++;
      while (i < lines.length && lines[i].trim() && !startsBlock(lines[i])) parts.push(lines[i++]);
      const paragraph = document.createElement('p'); appendInline(paragraph, parts.join('\n')); root.append(paragraph);
    }
  }

  function render(content, root) {
    root.replaceChildren(); root.classList.add('rich-message');
    renderLines(String(content ?? '').replace(/\r\n?/g, '\n').split('\n'), root);
  }

  return { render };
})();
