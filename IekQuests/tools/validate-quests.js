#!/usr/bin/env node
// Offline structural validation for IekQuests Quest bundle YAML files.
// Usage: node tools/validate-quests.js [Quest-directory-or-yml-file]
const fs = require('fs');
const path = require('path');
const yaml = require(path.join(__dirname, '..', '..', 'node_modules', 'js-yaml'));

const target = process.argv[2] || path.join(__dirname, '..', 'Quest');
const chapters = new Set([
  'server-tags', 'Reward', 'responses', 'quests', 'player-tags', 'Objective',
  'npcs', 'Journal', 'events', 'dialogues', 'Sequences'
]);
const entityChapters = new Set(['Reward', 'responses', 'quests', 'Objective', 'Journal', 'dialogues', 'Sequences']);
const errors = [];
const warnings = [];
const definitions = new Map();
const npcIds = new Map();
const bundles = fs.existsSync(target)
  ? (fs.statSync(target).isDirectory()
      ? fs.readdirSync(target).filter(name => name.toLowerCase().endsWith('.yml')).sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase())).map(name => path.join(target, name))
      : [target])
  : [];

const isMapping = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const reportDuplicate = (chapter, id, file) => {
  const key = `${chapter}/${id}`;
  const old = definitions.get(key);
  if (old) errors.push(`${path.basename(file)}: duplicate ${chapter} ID '${id}' also declared in ${path.basename(old)}.`);
  else definitions.set(key, file);
};

if (!bundles.length) {
  console.log(`No Quest bundle YAML files found at ${target}; nothing to validate.`);
  process.exit(0);
}

for (const file of bundles) {
  let bundle;
  try {
    bundle = yaml.load(fs.readFileSync(file, 'utf8')) || {};
  } catch (error) {
    errors.push(`${path.basename(file)}: YAML error at line ${error.mark ? error.mark.line + 1 : '?'}: ${error.reason || error.message}`);
    continue;
  }
  if (!isMapping(bundle)) {
    errors.push(`${path.basename(file)}: bundle root must be a YAML mapping.`);
    continue;
  }
  for (const [chapter, contents] of Object.entries(bundle)) {
    if (!chapters.has(chapter)) {
      errors.push(`${path.basename(file)}: unsupported chapter '${chapter}'.`);
      continue;
    }
    if (!isMapping(contents)) {
      errors.push(`${path.basename(file)}: chapter '${chapter}' must be a YAML mapping.`);
      continue;
    }
    if (chapter === 'player-tags' || chapter === 'server-tags') {
      const tags = contents.tags;
      if (tags != null && !Array.isArray(tags) && !isMapping(tags))
        errors.push(`${path.basename(file)}: ${chapter}.tags must be a list or mapping.`);
    }
    if (chapter === 'events') {
      for (const [eventType, entries] of Object.entries(contents)) {
        if (!isMapping(entries)) {
          errors.push(`${path.basename(file)}: events.${eventType} must be a mapping.`);
          continue;
        }
        for (const id of Object.keys(entries)) reportDuplicate(`events.${eventType}`, id, file);
      }
    }
    if (entityChapters.has(chapter)) {
      for (const id of Object.keys(contents)) {
        if (!/^[A-Za-z0-9_-]{1,64}$/.test(id)) errors.push(`${path.basename(file)}: malformed ${chapter} ID '${id}'.`);
        reportDuplicate(chapter, id, file);
      }
    }
    if (chapter === 'npcs') {
      for (const [key, binding] of Object.entries(contents)) {
        if (!isMapping(binding) || !Number.isInteger(Number(binding['npc-id'])) || Number(binding['npc-id']) < 0) {
          errors.push(`${path.basename(file)}: NPC binding '${key}' must have a non-negative integer npc-id.`);
          continue;
        }
        const id = Number(binding['npc-id']);
        const old = npcIds.get(id);
        if (old && JSON.stringify(old.visibility) !== JSON.stringify(binding['visibility-prerequisites'] || []))
          errors.push(`${path.basename(file)}: Citizens NPC ${id} has conflicting visibility-prerequisites (first used in ${path.basename(old.file)}).`);
        else if (old) warnings.push(`Citizens NPC ${id} appears in multiple bundles; IekQuests will merge its dialogue lists.`);
        else npcIds.set(id, {file, visibility: binding['visibility-prerequisites'] || []});
      }
    }
  }
}

errors.forEach(message => console.error(`ERROR: ${message}`));
warnings.forEach(message => console.log(`warning: ${message}`));
console.log(`${bundles.length} bundle file(s), ${definitions.size} named definition(s), ${errors.length} error(s), ${warnings.length} warning(s).`);
process.exit(errors.length ? 1 : 0);
