const CHANNEL_LABELS = { stable: 'stable', rc: 'release candidate' };
const DEFAULT_PROJECT_NAME = 'demo';
const WITH_LIBRARY_PROJECTS = 'with-library-projects';
const SKILL_PATH = '/.well-known/agent-skills/gradle-new/SKILL.md';
const CLAUDE_CODE_SKILL = '~/.claude/skills/gradle-new/SKILL.md';

export function initialChoices(model) {
  const componentType = firstComponentType(model);
  const language = languagesFor(model, componentType)[0] ?? null;
  const type = buildTypeFor(model, componentType, language);
  return {
    channel: 'stable',
    componentType,
    language,
    dsl: type.dsls.default,
    testFramework: testFrameworkChoice(type, false).default,
    splitProject: false,
    projectName: DEFAULT_PROJECT_NAME,
    packageName: model.packageName.default ?? '',
    javaVersion: String(model.javaVersion.default),
    incubating: false,
    comments: true,
  };
}

export function normalize(model, versions, partial = {}) {
  const choices = { ...initialChoices(model), ...partial };
  if (choices.channel === 'rc' && !versions?.rc) choices.channel = 'stable';
  if (!componentTypes(model).some((c) => c.id === choices.componentType)) choices.componentType = firstComponentType(model);
  const languages = languagesFor(model, choices.componentType);
  if (!languages.includes(choices.language)) choices.language = languages[0] ?? null;
  const type = buildTypeFor(model, choices.componentType, choices.language);
  if (!type.dsls.values.includes(choices.dsl)) choices.dsl = type.dsls.default;
  if (!offersSplit(type)) choices.splitProject = false;
  const frameworks = testFrameworkChoice(type, choices.splitProject);
  if (!frameworks.values.includes(choices.testFramework)) choices.testFramework = frameworks.default;
  choices.splitProject = Boolean(choices.splitProject);
  choices.incubating = Boolean(choices.incubating);
  choices.comments = Boolean(choices.comments);
  return { choices, type };
}

export function formState(model, versions, partial = {}) {
  const { choices, type } = normalize(model, versions, partial);
  return {
    choices,
    buildType: type.id,
    channels: channelChoices(versions),
    componentTypes: componentTypes(model).map((c) => ({ id: c.id, label: c.displayName })),
    languages: languagesFor(model, choices.componentType).map((id) => ({ id, label: named(model.languages, id) })),
    dsls: type.dsls.values.map((id) => ({ id, label: named(model.dsls, id) })),
    testFrameworks: testFrameworkChoice(type, choices.splitProject).values.map((id) => ({ id, label: named(model.testFrameworks, id) })),
    splitProject: offersSplit(type),
    packageName: type.supportsPackage,
    javaVersion: { supported: type.supportsJavaVersion, minimum: model.javaVersion.minimum, maximum: model.javaVersion.maximum },
  };
}

export function validate(model, partial = {}) {
  const { choices, type } = normalize(model, null, partial);
  const errors = {};
  if (!choices.projectName.trim()) errors.projectName = 'Enter a project name.';
  if (type.supportsPackage) {
    const pattern = model.packageName.pattern ? new RegExp(model.packageName.pattern) : null;
    if (!choices.packageName.trim()) errors.packageName = 'Enter a package, for example org.example.';
    else if (pattern && !pattern.test(choices.packageName)) errors.packageName = `'${choices.packageName}' is not a valid package name.`;
  }
  if (type.supportsJavaVersion) {
    const version = Number(choices.javaVersion);
    if (!/^\d+$/.test(choices.javaVersion)) errors.javaVersion = 'Enter a whole number.';
    else if (version < model.javaVersion.minimum) errors.javaVersion = `Gradle supports Java ${model.javaVersion.minimum} and later.`;
  }
  return errors;
}

export function commandTokens(model, versions, partial = {}) {
  const { choices, type } = normalize(model, versions, partial);
  const version = versionOf(versions, choices.channel);
  const tokens = [
    { id: 'gradle', text: 'gradle', kind: 'gradle', description: `Runs Gradle ${version}, the version the ${CHANNEL_LABELS[choices.channel]} channel currently points to.` },
    { id: 'init', text: 'init', kind: 'init', description: "Runs Gradle's init task, which generates a new build in the current directory." },
  ];
  for (const parameter of parameters(model, choices, type)) {
    if (parameter.flag) {
      const option = parameter.value ? parameter.name : `no-${parameter.name}`;
      tokens.push(optionToken(model, option, `--${option}`, parameter.name, parameter.value ? 'description' : 'negatedDescription'));
    } else {
      tokens.push({ ...optionToken(model, parameter.name, `--${parameter.name} ${parameter.value}`, parameter.name, 'description'), value: parameter.value, valueLabel: valueLabel(model, parameter.name, parameter.value) });
    }
  }
  tokens.push(optionToken(model, 'use-defaults', '--use-defaults', 'use-defaults', 'description'));
  return tokens;
}

export function commandLine(model, versions, partial = {}) {
  return commandTokens(model, versions, partial).map((t) => t.text).join(' ');
}

export function urlTokens(model, versions, partial = {}, origin = '') {
  const { choices, type } = normalize(model, versions, partial);
  const offered = channels(versions).map((c) => `${c.id} is currently Gradle ${c.version}`).join(', ');
  const tokens = [
    { id: 'endpoint', text: `${origin}/api/generate`, kind: 'endpoint', description: `The gradle.new API. It generates the same build and returns it as a zip archive. Options left out take Gradle's defaults, and without a version it uses the stable channel, currently Gradle ${versionOf(versions, 'stable')}.` },
  ];
  if (choices.channel !== 'stable') {
    tokens.push({ id: 'param-version', text: `version=${choices.channel}`, kind: 'version', description: `The Gradle version channel: ${offered}.` });
  }
  for (const parameter of parameters(model, choices, type)) {
    const value = String(parameter.value);
    const token = optionToken(model, parameter.name, `${parameter.name}=${encodeURIComponent(value)}`, parameter.name, 'description');
    token.id = `param-${parameter.name}`;
    if (parameter.flag) token.valueSetting = parameter.value ? 'Set to true.' : 'Set to false.';
    else { token.value = value; token.valueLabel = valueLabel(model, parameter.name, value); }
    tokens.push(token);
  }
  tokens.slice(1).forEach((token, index) => { token.separator = index === 0 ? '?' : '&'; });
  return tokens;
}

export function apiUrl(model, versions, partial = {}, origin = '') {
  const [endpoint, ...params] = urlTokens(model, versions, partial, origin);
  return endpoint.text + params.map((p) => p.separator + p.text).join('');
}

export function installTokens(tool, origin) {
  if (tool === 'curl') {
    return [
      { id: 'curl', text: 'curl', kind: 'program', description: 'Downloads one file over HTTP. Nothing is run.' },
      { id: 'curl-flags', text: '-fsSL', kind: 'flag', description: 'Fails on an HTTP error instead of saving the error page (-f), shows errors but no progress (-sS), and follows redirects (-L).' },
      { id: 'create-dirs', text: '--create-dirs', kind: 'flag', description: "Creates the skill's folder if it does not exist yet." },
      { id: 'output', text: `-o ${CLAUDE_CODE_SKILL}`, kind: 'flag', flag: '-o', value: CLAUDE_CODE_SKILL, description: "Saves the skill as a personal skill of Claude Code, available in every project. Other agents read skills from their own folders, which the npx command covers." },
      { id: 'skill-url', text: `${origin}${SKILL_PATH}`, kind: 'address', description: 'The gradle-new skill on this website, a single Markdown file you can read first.' },
    ];
  }
  return [
    { id: 'npx', text: 'npx', kind: 'program', description: 'Runs the latest skills command-line tool from the npm registry with Node.js, downloading it first if needed.' },
    { id: 'skills-add', text: 'skills add', kind: 'program', description: 'Installs agent skills from a website or repository, checking each one against the digest its source publishes. The open-source skills CLI sends anonymous usage data to skills.sh unless DISABLE_TELEMETRY=1 is set.' },
    { id: 'site', text: origin, kind: 'address', description: 'This website. The CLI reads its skill index at /.well-known/agent-skills/index.json and downloads the gradle-new skill it lists.' },
    { id: 'global', text: '-g', kind: 'flag', description: 'Installs the skill for your user, available in every project, instead of into the current project. The CLI asks which of your coding agents to install it for.' },
  ];
}

export function installCommand(tool, origin) {
  return installTokens(tool, origin).map((t) => t.text).join(' ');
}

export function channelChoices(versions) {
  return [
    { id: 'stable', version: versions?.stable ?? null, available: Boolean(versions?.stable) },
    { id: 'rc', version: versions?.rc ?? null, available: Boolean(versions?.rc) },
  ];
}

export function channels(versions) {
  const result = [{ id: 'stable', version: versions?.stable }];
  if (versions?.rc) result.push({ id: 'rc', version: versions.rc });
  return result;
}

export function versionOf(versions, channel) {
  return channel === 'rc' && versions?.rc ? versions.rc : versions?.stable;
}

function parameters(model, choices, type) {
  const result = [{ name: 'type', value: type.id, required: true }];
  if (type.dsls.values.length > 0) result.push({ name: 'dsl', value: choices.dsl, default: type.dsls.default });
  if (choices.testFramework) result.push({ name: 'test-framework', value: choices.testFramework, default: testFrameworkChoice(type, choices.splitProject).default });
  if (type.supportsProjectName) result.push({ name: 'project-name', value: choices.projectName, required: true });
  if (type.supportsPackage) result.push({ name: 'package', value: choices.packageName, default: model.packageName.default });
  if (type.supportsJavaVersion) result.push({ name: 'java-version', value: choices.javaVersion, default: String(model.javaVersion.default) });
  if (offersSplit(type)) result.push({ name: 'split-project', value: choices.splitProject, flag: true, default: type.modularization.default === WITH_LIBRARY_PROJECTS });
  result.push({ name: 'incubating', value: choices.incubating, flag: true, default: false });
  result.push({ name: 'comments', value: choices.comments, flag: true, default: true });
  return result.filter((p) => p.required || String(p.value) !== String(p.default));
}

function optionToken(model, option, text, name, field) {
  const definition = model.options.find((o) => o.name === name);
  return { id: `option-${option}`, text, kind: 'option', option, description: definition?.[field] ?? '' };
}

function valueLabel(model, option, value) {
  switch (option) {
    case 'type': {
      const type = model.buildTypes.find((t) => t.id === value);
      const component = named(model.componentTypes, type.componentType);
      return type.language ? `${component} in ${named(model.languages, type.language)}` : component;
    }
    case 'dsl': return `${named(model.dsls, value)} DSL`;
    case 'test-framework': return named(model.testFrameworks, value);
    default: return null;
  }
}

function named(vocabulary, id) {
  return vocabulary.find((v) => v.id === id)?.displayName ?? id;
}

function componentTypes(model) {
  return model.componentTypes.filter((c) => model.buildTypes.some((t) => t.componentType === c.id));
}

function firstComponentType(model) {
  return componentTypes(model)[0].id;
}

function languagesFor(model, componentType) {
  const used = model.buildTypes.filter((t) => t.componentType === componentType).map((t) => t.language).filter(Boolean);
  return model.languages.map((l) => l.id).filter((id) => used.includes(id));
}

function buildTypeFor(model, componentType, language) {
  return model.buildTypes.find((t) => t.componentType === componentType && t.language === language)
    ?? model.buildTypes.find((t) => t.componentType === componentType);
}

function offersSplit(type) {
  return type.modularization.values.length > 1 && type.modularization.values.includes(WITH_LIBRARY_PROJECTS);
}

function testFrameworkChoice(type, split) {
  const modularization = split && offersSplit(type) ? WITH_LIBRARY_PROJECTS : type.modularization.values.find((m) => m !== WITH_LIBRARY_PROJECTS) ?? type.modularization.default;
  return type.testFrameworks.find((c) => c.modularization === modularization) ?? { values: [], default: null };
}
