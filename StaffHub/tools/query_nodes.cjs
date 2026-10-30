const {DatabaseSync} = require('node:sqlite');
const db = new DatabaseSync('data/staffhub.db');
const nodes = db.prepare("SELECT id, parent, name, type FROM nodes").all();
console.log(JSON.stringify(nodes, null, 2));