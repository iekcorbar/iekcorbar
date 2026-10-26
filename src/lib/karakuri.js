// Karakuri Arm language (Steamworks plugin) - a JavaScript twin of the plugin's
// ArmProgram.java, used by /steamworks to check programs in the browser.
// Keep both in step: same instructions, same limits, same error texts.

export const MAX_POINTS = 8;

export const INSTRUCTIONS = [
  { name: 'PICK', syntax: 'PICK p [count]', tool: 'gripper / placer', text: 'Take items from point p into the claw (a full stack, or up to count). Does nothing if the claw already holds something.' },
  { name: 'DROP', syntax: 'DROP p', tool: 'gripper / placer', text: 'Put the claw\'s items into point p: a chest, barrel, hopper, millstone, kettle, conveyor belt, or the ground. What does not fit stays in the claw.' },
  { name: 'PLACE', syntax: 'PLACE p', tool: 'placer', text: 'Place one block from the claw at point p (the spot must be empty, like air or grass).' },
  { name: 'USE', syntax: 'USE p', tool: 'placer', text: 'Use the item in the claw on point p. Right now: bone meal on crops, saplings and grass.' },
  { name: 'BREAK', syntax: 'BREAK p', tool: 'drill head', text: 'Break the block at point p. Drops what an iron pickaxe would get. Never breaks chests or Steamworks parts.' },
  { name: 'WAIT', syntax: 'WAIT seconds', tool: 'any', text: 'Pause for a number of seconds (0.5 works too, max 3600).' },
  { name: 'WAIT REDSTONE', syntax: 'WAIT REDSTONE', tool: 'any', text: 'Pause until the arm gets a redstone signal (a lever, button or redstone dust next to it).' },
  { name: 'WAIT ITEM', syntax: 'WAIT ITEM p', tool: 'any', text: 'Pause until there is something at point p to pick up.' },
  { name: 'LEVER', syntax: 'LEVER p ON|OFF', tool: 'any', text: 'Flip a lever at point p on or off - use it to start other redstone machines.' },
  { name: 'LOOP', syntax: 'LOOP ... END', tool: 'any', text: 'Repeat everything up to the matching END forever.' },
  { name: 'REPEAT', syntax: 'REPEAT n ... END', tool: 'any', text: 'Do everything up to the matching END n times.' },
  { name: 'IF', syntax: 'IF condition ... END', tool: 'any', text: 'Only do the lines up to END when the condition is true. Conditions: EMPTY p, FULL p, HAS p, REDSTONE, HOLDING.' },
];

export const CONDITIONS = [
  { name: 'EMPTY p', text: 'nothing to pick up at point p' },
  { name: 'HAS p', text: 'there is something to pick up at point p' },
  { name: 'FULL p', text: 'the container at point p has no free room' },
  { name: 'REDSTONE', text: 'the arm gets a redstone signal' },
  { name: 'HOLDING', text: 'the claw holds something' },
];

class ProgramError extends Error {
  constructor(line, msg) {
    super(`line ${line}: ${msg}`);
    this.line = line;
  }
}

function num(s, ln, min, max) {
  if (!/^-?\d+$/.test(s)) throw new ProgramError(ln, `'${s}' is not a number`);
  const v = parseInt(s, 10);
  if (v < min || v > max) throw new ProgramError(ln, `${s} must be ${min}-${max}`);
  return v;
}

function point(w, i, ln, maxPoint) {
  if (w.length <= i) throw new ProgramError(ln, `${w[0]} needs a point number`);
  const p = num(w[i], ln, 1, 8);
  if (p > maxPoint) throw new ProgramError(ln, `point ${p} is not taught yet (the arm has ${maxPoint})`);
  return p;
}

/** Same rules as ArmProgram.compile. Returns { ops } or throws ProgramError. */
export function compile(source, maxPoint) {
  const ops = [];
  const open = [];
  const lines = (source || '').split(/\r?\n/);
  lines.forEach((raw, i) => {
    const ln = i + 1;
    let line = raw;
    const hash = line.indexOf('#');
    if (hash >= 0) line = line.slice(0, hash);
    line = line.trim();
    if (!line) return;
    const w = line.toUpperCase().split(/\s+/);
    let op;
    switch (w[0]) {
      case 'PICK': op = { code: 'PICK', a: point(w, 1, ln, maxPoint), b: w.length > 2 ? num(w[2], ln, 1, 64) : 64 }; break;
      case 'DROP': op = { code: 'DROP', a: point(w, 1, ln, maxPoint) }; break;
      case 'PLACE': op = { code: 'PLACE', a: point(w, 1, ln, maxPoint) }; break;
      case 'USE': op = { code: 'USE', a: point(w, 1, ln, maxPoint) }; break;
      case 'BREAK': op = { code: 'BREAK', a: point(w, 1, ln, maxPoint) }; break;
      case 'LEVER':
        if (w.length < 3 || !(w[2] === 'ON' || w[2] === 'OFF')) throw new ProgramError(ln, 'LEVER needs a point and ON or OFF');
        op = { code: 'LEVER', a: point(w, 1, ln, maxPoint), b: w[2] === 'ON' ? 1 : 0 };
        break;
      case 'WAIT':
        if (w.length < 2) throw new ProgramError(ln, 'WAIT needs seconds, REDSTONE or ITEM p');
        if (w[1] === 'REDSTONE') op = { code: 'WAIT_REDSTONE' };
        else if (w[1] === 'ITEM') op = { code: 'WAIT_ITEM', a: point(w, 2, ln, maxPoint) };
        else {
          const secs = Number(w[1]);
          if (!/^(\d+(\.\d*)?|\.\d+)$/.test(w[1]) || !Number.isFinite(secs)) throw new ProgramError(ln, `'${w[1]}' is not a number of seconds`);
          if (secs < 0 || secs > 3600) throw new ProgramError(ln, 'WAIT seconds must be 0-3600');
          op = { code: 'WAIT', a: Math.round(secs * 20) };
        }
        break;
      case 'LOOP': op = { code: 'LOOP' }; break;
      case 'REPEAT':
        if (w.length < 2) throw new ProgramError(ln, 'REPEAT needs a count');
        op = { code: 'REPEAT', a: num(w[1], ln, 0, 10000) };
        break;
      case 'IF':
        if (w.length < 2) throw new ProgramError(ln, 'IF needs EMPTY p, FULL p, HAS p, REDSTONE or HOLDING');
        switch (w[1]) {
          case 'EMPTY': op = { code: 'IF_EMPTY', a: point(w, 2, ln, maxPoint) }; break;
          case 'FULL': op = { code: 'IF_FULL', a: point(w, 2, ln, maxPoint) }; break;
          case 'HAS': op = { code: 'IF_HAS', a: point(w, 2, ln, maxPoint) }; break;
          case 'REDSTONE': op = { code: 'IF_REDSTONE' }; break;
          case 'HOLDING': op = { code: 'IF_HOLDING' }; break;
          default: throw new ProgramError(ln, `unknown IF condition '${w[1]}'`);
        }
        break;
      case 'END': {
        if (!open.length) throw new ProgramError(ln, 'END without LOOP, REPEAT or IF');
        const start = open.pop();
        ops[start].jump = ops.length;
        op = { code: 'END', jump: start };
        break;
      }
      default: throw new ProgramError(ln, `unknown instruction '${w[0]}'`);
    }
    op.line = ln;
    op.depth = open.length;
    if (op.code === 'LOOP' || op.code === 'REPEAT' || op.code.startsWith('IF_')) open.push(ops.length);
    ops.push(op);
    if (ops.length > 512) throw new ProgramError(ln, 'program too long (max 512 instructions)');
  });
  if (open.length) {
    const o = ops[open[open.length - 1]];
    throw new ProgramError(o.line, `${o.code} has no END`);
  }
  return { ops };
}

/** Which instructions need which tool head (for warnings in the checker). */
export function toolProblems(ops, tool) {
  const claw = tool === 'gripper' || tool === 'placer';
  const out = [];
  for (const op of ops) {
    if ((op.code === 'PICK' || op.code === 'DROP') && !claw) out.push(`line ${op.line}: ${op.code} needs a gripper or placer head`);
    if ((op.code === 'PLACE' || op.code === 'USE') && tool !== 'placer') out.push(`line ${op.line}: ${op.code} needs a placer head`);
    if (op.code === 'BREAK' && tool !== 'drill') out.push(`line ${op.line}: BREAK needs a drill head`);
  }
  return out;
}

/** One plain-English sentence per instruction. */
export function explain(op) {
  switch (op.code) {
    case 'PICK': return `Take ${op.b === 64 ? 'a stack' : `up to ${op.b}`} from point ${op.a}.`;
    case 'DROP': return `Put what it holds into point ${op.a}.`;
    case 'PLACE': return `Place a block at point ${op.a}.`;
    case 'USE': return `Use the held item on point ${op.a}.`;
    case 'BREAK': return `Break the block at point ${op.a}.`;
    case 'WAIT': return `Wait ${op.a / 20} second${op.a === 20 ? '' : 's'}.`;
    case 'WAIT_REDSTONE': return 'Wait for a redstone signal.';
    case 'WAIT_ITEM': return `Wait until there is something at point ${op.a}.`;
    case 'LEVER': return `Switch the lever at point ${op.a} ${op.b ? 'on' : 'off'}.`;
    case 'LOOP': return 'Repeat the following forever:';
    case 'REPEAT': return `Do the following ${op.a} time${op.a === 1 ? '' : 's'}:`;
    case 'IF_EMPTY': return `If point ${op.a} is empty:`;
    case 'IF_FULL': return `If point ${op.a} is full:`;
    case 'IF_HAS': return `If point ${op.a} has something:`;
    case 'IF_REDSTONE': return 'If there is a redstone signal:';
    case 'IF_HOLDING': return 'If the claw holds something:';
    case 'END': return 'End of block.';
    default: return '';
  }
}

export const EXAMPLES = [
  {
    title: 'Chest to chest',
    tool: 'gripper', points: 2,
    setup: 'Point 1 = a chest with items, point 2 = an empty chest.',
    code: '# move everything from chest 1 to chest 2\nPICK 1\nDROP 2\n',
  },
  {
    title: 'Feed the millstone',
    tool: 'gripper', points: 3,
    setup: 'Point 1 = chest with cobblestone, point 2 = millstone, point 3 = output chest.',
    code: 'LOOP\n  PICK 1 16      # 16 cobblestone\n  DROP 2         # into the millstone\n  WAIT 10\n  PICK 2         # take the gravel out\n  DROP 3\nEND\n',
  },
  {
    title: 'Only on a signal',
    tool: 'gripper', points: 2,
    setup: 'A button or lever next to the arm. Point 1 = chest, point 2 = conveyor belt.',
    code: 'LOOP\n  WAIT REDSTONE\n  PICK 1 8\n  DROP 2\n  WAIT 1\nEND\n',
  },
  {
    title: 'Tree farm',
    tool: 'placer', points: 3,
    setup: 'Point 1 = chest with saplings. Point 2 = the empty spot on top of a dirt block where the tree grows. Point 3 = chest with bone meal. (Cut the tree with a Mechanical Saw.)',
    code: 'PICK 1 1         # one sapling\nPLACE 2\nPICK 3 4         # some bone meal\nREPEAT 4\n  USE 2\nEND\nDROP 3           # put leftover bone meal back\nWAIT 5\n',
  },
  {
    title: 'Stop when the chest is full',
    tool: 'gripper', points: 3,
    setup: 'Point 1 = input, point 2 = output chest, point 3 = a lever that switches a lamp.',
    code: 'LOOP\n  IF FULL 2\n    LEVER 3 ON     # warning lamp on\n    WAIT 5\n  END\n  IF HAS 1\n    LEVER 3 OFF\n    PICK 1\n    DROP 2\n  END\n  WAIT 1\nEND\n',
  },
];
