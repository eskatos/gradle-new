import { apiUrl, commandLine, commandTokens, formState, installCommand, installTokens, urlTokens, validate, versionOf } from './model.js';

const SVG = 'http://www.w3.org/2000/svg';
const FIELD_OF_PARAMETER = { 'project-name': 'projectName', package: 'packageName', 'java-version': 'javaVersion' };
const INPUT_OF_FIELD = { projectName: 'project-name', packageName: 'package', javaVersion: 'java-version' };
const CHANNEL_CHOICES = { stable: 'Stable', rc: 'Release candidate', previous: 'Previous major' };
const LINES_OF_SNIPPET = { command: '#command', url: '#api-url', skill: '#skill-command' };
const COPY_OF_SNIPPET = { command: '#copy-command', url: '#copy-url', skill: '#copy-skill' };
const WRAPPED_INSTALL_TOKENS = new Set(['output', 'skill-url']);

const state = { versions: null, models: {}, choices: {}, touched: new Set(), serverErrors: {}, busy: false, tool: 'npx', active: { command: {}, url: {}, skill: {} } };

const $ = (selector, root = document) => root.querySelector(selector);

start().catch((error) => {
  $('#form-loading').textContent = 'The options of Gradle could not be loaded. Reload the page to try again.';
  console.error(error);
});

async function start() {
  bindSkill();
  state.versions = await json('/api/versions');
  await modelFor('stable');
  document.querySelectorAll('[data-stable-version]').forEach((element) => { element.textContent = state.versions.stable; });
  bindForm();
  bindSnippet('command', () => commandLine(model(), state.versions, state.choices));
  bindSnippet('url', () => apiUrl(model(), state.versions, state.choices, location.origin));
  render();
  $('#form-loading').hidden = true;
  $('#form-fields').hidden = false;
  new ResizeObserver(() => drawAllConnectors()).observe(document.body);
  document.fonts?.ready.then(() => drawAllConnectors());
}

async function json(path) {
  const response = await fetch(path, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`${path}: ${response.status}`);
  return response.json();
}

async function modelFor(channel) {
  const version = versionOf(state.versions, channel);
  if (!state.models[version]) state.models[version] = await json(`/v/${encodeURIComponent(version)}/model.json`);
  return state.models[version];
}

function model() {
  return state.models[versionOf(state.versions, state.choices.channel ?? 'stable')];
}

function bindForm() {
  const form = $('#form');
  form.addEventListener('change', (event) => onInput(event.target));
  form.addEventListener('input', (event) => { if (event.target.matches('input[type="text"]')) onInput(event.target); });
  form.addEventListener('submit', (event) => { event.preventDefault(); download(); });
}

async function onInput(target) {
  const { name } = target;
  if (target.type === 'radio') {
    if (!target.checked) return;
    const key = { version: 'channel', 'component-type': 'componentType', language: 'language', dsl: 'dsl', 'test-framework': 'testFramework' }[name];
    if (key === 'channel') await modelFor(target.value);
    state.choices[key] = target.value;
  } else if (target.type === 'checkbox') {
    state.choices[{ 'split-project': 'splitProject', incubating: 'incubating', comments: 'comments' }[target.id]] = target.checked;
  } else if (target.type === 'text') {
    const field = FIELD_OF_PARAMETER[target.id];
    state.choices[field] = target.value;
    state.touched.add(field);
    delete state.serverErrors[field];
  }
  $('#form-status').textContent = '';
  showFormError(null);
  render();
}

function render() {
  const current = model();
  const form = formState(current, state.versions, state.choices);
  state.choices = form.choices;

  renderChoices('version', form.channels.map((c) => ({ id: c.id, label: CHANNEL_CHOICES[c.id], note: c.version ?? 'None available', version: Boolean(c.version), disabled: !c.available, channel: c.id })), form.choices.channel);
  renderChoices('component-type', form.componentTypes, form.choices.componentType);
  renderChoices('language', form.languages, form.choices.language);
  renderChoices('dsl', form.dsls, form.choices.dsl);
  renderChoices('test-framework', form.testFrameworks, form.choices.testFramework);

  show('#field-language', form.languages.length > 0);
  show('#field-dsl', form.dsls.length > 1);
  show('#field-test-framework', form.testFrameworks.length > 0);
  show('#field-package', form.packageName);
  show('#field-java-version', form.javaVersion.supported);
  show('#field-split-project', form.splitProject);

  setValue('#project-name', form.choices.projectName);
  setValue('#package', form.choices.packageName);
  setValue('#java-version', form.choices.javaVersion);
  $('#split-project').checked = form.choices.splitProject;
  $('#incubating').checked = form.choices.incubating;
  $('#comments').checked = form.choices.comments;

  renderHints(current);
  renderErrors(validate(current, form.choices));
  renderSnippet('command', commandTokens(current, state.versions, form.choices), commandLines);
  renderSnippet('url', urlTokens(current, state.versions, form.choices, location.origin), urlLines);
}

function renderChoices(group, options, selected) {
  const container = $(`[data-group="${group}"]`);
  const signature = options.map((o) => `${o.id}:${o.label}:${o.note ?? ''}:${Boolean(o.disabled)}`).join('|');
  if (container.dataset.signature !== signature) {
    container.replaceChildren(...options.map((option) => {
      const label = document.createElement('label');
      label.className = 'choice';
      if (option.channel) label.dataset.channel = option.channel;
      const input = document.createElement('input');
      input.type = 'radio';
      input.name = group;
      input.value = option.id;
      input.disabled = Boolean(option.disabled);
      const text = document.createElement('span');
      text.className = 'choice-label';
      text.textContent = option.label;
      label.append(input, text);
      if (option.note) {
        const note = document.createElement('span');
        note.className = option.version ? 'choice-note channel-version' : 'choice-note';
        note.textContent = option.note;
        label.append(note);
      }
      return label;
    }));
    container.dataset.signature = signature;
  }
  container.querySelectorAll('input').forEach((input) => { input.checked = input.value === selected; });
}

function renderHints(current) {
  document.querySelectorAll('[data-hint]').forEach((hint) => {
    hint.textContent = current.options.find((o) => o.name === hint.dataset.hint)?.description ?? '';
  });
}

function renderErrors(clientErrors) {
  for (const [field, id] of Object.entries(INPUT_OF_FIELD)) {
    const message = state.serverErrors[field] ?? (state.touched.has(field) ? clientErrors[field] : undefined);
    const input = $(`#${id}`);
    const error = $(`#${id}-error`);
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    error.hidden = !message;
    $('.error-text', error).textContent = message ?? '';
  }
}

function showFormError(message) {
  const error = $('#form-error');
  error.hidden = !message;
  $('.error-text', error).textContent = message ?? '';
}

function show(selector, visible) {
  $(selector).hidden = !visible;
}

function setValue(selector, value) {
  const input = $(selector);
  if (input.value !== value) input.value = value;
}

function commandLines(tokens) {
  const [gradle, init, ...options] = tokens;
  return [[gradle, ' ', init], ...options.map((token) => ['  ', token])];
}

function urlLines(tokens) {
  const [endpoint, ...parameters] = tokens;
  return [[endpoint], ...parameters.map((token) => ['  ', { kind: 'separator', text: token.separator }, token])];
}

function installLines(tokens) {
  const lines = [];
  for (const token of tokens) {
    if (lines.length > 0 && !WRAPPED_INSTALL_TOKENS.has(token.id)) lines.at(-1).push(' ', token);
    else lines.push(lines.length === 0 ? [token] : ['  ', token]);
  }
  return lines;
}

function renderSnippet(name, tokens, layout) {
  const pre = $(LINES_OF_SNIPPET[name]);
  const lines = layout(tokens);
  const nodes = [];
  lines.forEach((parts, index) => {
    for (const part of parts) {
      if (typeof part === 'string') nodes.push(document.createTextNode(part));
      else if (part.kind === 'separator') nodes.push(span('token-separator', part.text));
      else nodes.push(tokenElement(part, name));
    }
    if (index < lines.length - 1) {
      if (name !== 'url') nodes.push(span('continuation', ' \\'));
      nodes.push(document.createTextNode('\n'));
    }
  });
  pre.replaceChildren(...nodes);
  renderExplanation(name, tokens);
  applyHighlight(name);
}

function tokenElement(token, snippet) {
  const element = span(`token token-${token.kind}`, null);
  element.dataset.tokenId = token.id;
  if (token.kind === 'option' && snippet === 'command' && token.value !== undefined) {
    element.append(span('token-option', `--${token.option}`), document.createTextNode(' '), document.createTextNode(token.value));
  } else if (token.kind === 'option' && snippet === 'command') {
    element.append(span('token-option', token.text));
  } else if (token.kind === 'gradle' || token.kind === 'init' || token.kind === 'program') {
    element.classList.add('token-command');
    element.textContent = token.text;
  } else if (token.kind === 'flag') {
    element.append(span('token-option', token.flag ?? token.text));
    if (token.value !== undefined) element.append(document.createTextNode(` ${token.value}`));
  } else {
    const [name, ...rest] = token.text.split('=');
    if (rest.length > 0) element.append(span('token-option', name), document.createTextNode(`=${rest.join('=')}`));
    else element.textContent = token.text;
  }
  element.addEventListener('click', () => explain(snippet, token.id));
  element.addEventListener('mouseenter', () => highlight(snippet, 'hover', token.id));
  element.addEventListener('mouseleave', () => highlight(snippet, 'hover', null));
  return element;
}

function renderExplanation(name, tokens) {
  const list = $(`#explain-${name} .explanation-list`);
  list.replaceChildren(...tokens.map((token) => {
    const item = document.createElement('div');
    item.className = 'explanation-item';
    item.tabIndex = 0;
    item.dataset.tokenId = token.id;
    if (token.kind === 'option') item.dataset.option = token.option;
    else item.dataset.kind = token.kind;
    if (token.value !== undefined && token.valueLabel) item.dataset.value = token.value;
    const term = document.createElement('dt');
    term.className = 'explanation-token';
    term.textContent = token.text;
    const definition = document.createElement('dd');
    const description = document.createElement('p');
    description.className = 'explanation-description';
    description.textContent = token.description;
    const value = document.createElement('p');
    value.className = 'explanation-value';
    value.textContent = token.valueLabel ?? token.valueSetting ?? '';
    definition.append(description, value);
    item.append(term, definition);
    item.addEventListener('mouseenter', () => highlight(name, 'hover', token.id));
    item.addEventListener('mouseleave', () => highlight(name, 'hover', null));
    item.addEventListener('focus', () => highlight(name, 'focus', token.id));
    item.addEventListener('blur', () => highlight(name, 'focus', null));
    return item;
  }));
  drawConnectors(name);
}

function bindSnippet(name, text) {
  const toggle = $(`#explain-${name}-toggle`);
  const panel = $(`#explain-${name}`);
  toggle.addEventListener('click', () => setExpanded(name, panel.hidden));
  const copy = $(COPY_OF_SNIPPET[name]);
  copy.addEventListener('click', async () => {
    await copyText(text());
    const label = $('.copy-label', copy);
    const icon = $('.icon', copy);
    copy.classList.add('is-copied');
    label.textContent = 'Copied';
    icon.classList.replace('icon-copy', 'icon-check');
    clearTimeout(copy.timer);
    copy.timer = setTimeout(() => {
      copy.classList.remove('is-copied');
      label.textContent = 'Copy';
      icon.classList.replace('icon-check', 'icon-copy');
    }, 1600);
  });
}

async function copyText(text) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const area = document.createElement('textarea');
  area.value = text;
  area.setAttribute('readonly', '');
  area.className = 'visually-hidden';
  document.body.append(area);
  area.select();
  document.execCommand('copy');
  area.remove();
}

function bindSkill() {
  const tabs = [...document.querySelectorAll('#skill-tabs [role="tab"]')];
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => selectTool(tab.dataset.tool));
    tab.addEventListener('keydown', (event) => {
      const next = { ArrowLeft: index - 1, ArrowRight: index + 1, Home: 0, End: tabs.length - 1 }[event.key];
      if (next === undefined) return;
      event.preventDefault();
      const target = tabs[(next + tabs.length) % tabs.length];
      selectTool(target.dataset.tool);
      target.focus();
    });
  });
  bindSnippet('skill', () => installCommand(state.tool, location.origin));
  selectTool(state.tool);
}

function selectTool(tool) {
  state.tool = tool;
  document.querySelectorAll('#skill-tabs [role="tab"]').forEach((tab) => {
    const selected = tab.dataset.tool === tool;
    tab.setAttribute('aria-selected', String(selected));
    tab.tabIndex = selected ? 0 : -1;
  });
  $('#skill-panel').setAttribute('aria-labelledby', `tab-${tool}`);
  state.active.skill = {};
  renderSnippet('skill', installTokens(tool, location.origin), installLines);
}

function setExpanded(name, expanded) {
  $(`#explain-${name}-toggle`).setAttribute('aria-expanded', String(expanded));
  $(`#explain-${name}`).hidden = !expanded;
  if (!expanded) state.active[name].pinned = null;
  drawConnectors(name);
  applyHighlight(name);
}

function explain(name, id) {
  if (String(getSelection()).length > 0) return;
  if ($(`#explain-${name}`).hidden) setExpanded(name, true);
  highlight(name, 'pinned', id);
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  $(`#explain-${name} .explanation-item[data-token-id="${CSS.escape(id)}"]`)?.scrollIntoView({ block: 'nearest', behavior: reduced ? 'auto' : 'smooth' });
}

function highlight(name, source, id) {
  state.active[name][source] = id;
  applyHighlight(name);
}

function applyHighlight(name) {
  const active = new Set(Object.values(state.active[name]).filter(Boolean));
  const pre = $(LINES_OF_SNIPPET[name]);
  const panel = $(`#explain-${name}`);
  for (const element of [...pre.querySelectorAll('.token'), ...panel.querySelectorAll('.explanation-item, path')]) {
    element.classList.toggle('is-active', active.has(element.dataset.tokenId));
  }
}

function drawAllConnectors() {
  Object.keys(LINES_OF_SNIPPET).forEach((name) => drawConnectors(name));
}

function drawConnectors(name) {
  const panel = $(`#explain-${name}`);
  const svg = $('svg.connectors', panel);
  if (panel.hidden || getComputedStyle(svg).display === 'none') {
    svg.replaceChildren();
    return;
  }
  const pre = $(LINES_OF_SNIPPET[name]);
  const origin = svg.getBoundingClientRect();
  const tokens = [...pre.querySelectorAll('.token')];
  const ends = tokens.map((token) => lineEnd(token) - origin.left);
  const start = Math.max(pre.getBoundingClientRect().right - origin.left, ...ends) + 12;
  const paths = tokens.map((token, index) => {
    const item = $(`.explanation-item[data-token-id="${token.dataset.tokenId}"]`, panel);
    const from = token.getBoundingClientRect();
    const to = item.getBoundingClientRect();
    const x1 = to.left - origin.left;
    const y1 = to.top + Math.min(18, to.height / 2) - origin.top;
    const bend = (x1 - start) / 2;
    let lead;
    let y0;
    if (lastOnLine(token)) {
      y0 = from.top + from.height / 2 - origin.top;
      lead = `M ${ends[index] + 6} ${y0} H ${start}`;
    } else {
      const top = from.top - origin.top;
      y0 = top - 4;
      lead = `M ${from.left + from.width / 2 - origin.left} ${top} V ${y0} H ${start}`;
    }
    const path = document.createElementNS(SVG, 'path');
    path.setAttribute('d', `${lead} C ${start + bend} ${y0}, ${x1 - bend} ${y1}, ${x1} ${y1}`);
    path.dataset.tokenId = token.dataset.tokenId;
    return path;
  });
  svg.replaceChildren(...paths);
  applyHighlight(name);
}

function lineEnd(token) {
  let node = token.nextSibling;
  let right = token.getBoundingClientRect().right;
  while (node && !(node.nodeType === Node.TEXT_NODE && node.textContent.includes('\n'))) {
    if (node.nodeType === Node.ELEMENT_NODE) right = Math.max(right, node.getBoundingClientRect().right);
    else if (node.textContent.trim()) {
      const range = document.createRange();
      range.selectNode(node);
      right = Math.max(right, range.getBoundingClientRect().right);
    }
    node = node.nextSibling;
  }
  return right;
}

function lastOnLine(token) {
  for (let node = token.nextSibling; node; node = node.nextSibling) {
    if (node.nodeType === Node.TEXT_NODE && node.textContent.includes('\n')) return true;
    if (node.nodeType === Node.ELEMENT_NODE && node.classList.contains('token')) return false;
  }
  return true;
}

async function download() {
  if (state.busy) return;
  const current = model();
  const errors = validate(current, state.choices);
  if (Object.keys(errors).length > 0) {
    Object.keys(errors).forEach((field) => state.touched.add(field));
    render();
    $(`#${INPUT_OF_FIELD[Object.keys(errors)[0]]}`).focus();
    return;
  }
  const button = $('#download');
  const label = $('.btn-label', button);
  const status = $('#form-status');
  state.busy = true;
  button.setAttribute('aria-busy', 'true');
  label.textContent = 'Generating…';
  status.textContent = '';
  showFormError(null);
  try {
    const response = await fetch(apiUrl(current, state.versions, state.choices, location.origin));
    if (response.status === 400) {
      const rejection = await response.json();
      const field = FIELD_OF_PARAMETER[rejection.parameter];
      if (field) {
        state.serverErrors[field] = rejection.error;
        render();
        $(`#${INPUT_OF_FIELD[field]}`).focus();
      } else {
        showFormError(rejection.error);
      }
      return;
    }
    if (response.status === 429) {
      const body = await response.json().catch(() => null);
      showFormError(body?.error ?? `Too many builds were requested in a short time. Try again in ${response.headers.get('Retry-After') ?? 'a few'} seconds.`);
      return;
    }
    if (!response.ok) throw new Error(`The server answered ${response.status}.`);
    const filename = filenameOf(response) ?? `${state.choices.projectName}.zip`;
    const blob = await response.blob();
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.hidden = true;
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(link.href), 60_000);
    status.textContent = `Downloaded ${filename}.`;
  } catch (error) {
    showFormError(`The build could not be generated. ${error.message} Try again in a moment.`);
  } finally {
    state.busy = false;
    button.removeAttribute('aria-busy');
    label.textContent = 'Download the build';
  }
}

function filenameOf(response) {
  const disposition = response.headers.get('Content-Disposition') ?? '';
  return /filename="([^"]+)"/.exec(disposition)?.[1] ?? null;
}

function span(className, text) {
  const element = document.createElement('span');
  element.className = className;
  if (text !== null) element.textContent = text;
  return element;
}
