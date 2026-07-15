#!/usr/bin/env node
// ops/db-scrub/remove_passwords.js
// Usage:
//   DRY RUN: node remove_passwords.js --dry-run --out backup.json
//   APPLY : node remove_passwords.js --apply --out backup.json
//
// Requirements:
// - Set GOOGLE_APPLICATION_CREDENTIALS pointing to a service account JSON with
//   proper Firebase Realtime Database Admin permissions.
// - Set DATABASE_URL env var to your RTDB URL (e.g., https://<PROJECT>.firebaseio.com)

const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

const args = require('minimist')(process.argv.slice(2), {
  boolean: ['dry-run', 'apply'],
  string: ['out']
});
const DRY_RUN = args['dry-run'] || (!args['apply']);
const APPLY = args['apply'] || false;
const OUT = args['out'] || `users-backup-${Date.now()}.json`;
const DATABASE_URL = process.env.DATABASE_URL;

if (!DATABASE_URL) {
  console.error('ERROR: DATABASE_URL env var is required (e.g. https://<project>.firebaseio.com)');
  process.exit(1);
}

console.log(`Mode: ${DRY_RUN ? 'DRY-RUN (no writes)' : 'APPLY (will remove password fields)'}`);
console.log(`Backup file: ${OUT}`);

try {
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    databaseURL: DATABASE_URL
  });
} catch (e) {
  console.error('Failed to initialize firebase-admin:', e);
  process.exit(1);
}

const db = admin.database();
const usersRef = db.ref('users');

async function run() {
  console.log('Reading users node (this can be large) ...');
  const snap = await usersRef.once('value');
  if (!snap.exists()) {
    console.log('No users node found; nothing to do.');
    process.exit(0);
  }

  const users = snap.val();
  // Backup entire users node to file
  fs.writeFileSync(OUT, JSON.stringify(users, null, 2), { encoding: 'utf8' });
  console.log(`Backup written to ${OUT} (size: ${fs.statSync(OUT).size} bytes)`);

  // Find users with password field
  const keysWithPassword = [];
  for (const [uid, data] of Object.entries(users)) {
    if (data && Object.prototype.hasOwnProperty.call(data, 'password')) {
      keysWithPassword.push(uid);
    }
  }

  console.log(`Found ${keysWithPassword.length} user(s) with a 'password' field.`);

  if (keysWithPassword.length === 0) {
    console.log('Nothing to remove. Exiting.');
    process.exit(0);
  }

  if (DRY_RUN && !APPLY) {
    console.log('DRY RUN mode: listing user ids that WOULD be modified:');
    keysWithPassword.forEach(k => console.log(' -', k));
    process.exit(0);
  }

  if (!APPLY) {
    console.log('Neither --dry-run nor --apply specified; exiting.');
    process.exit(1);
  }

  console.log('APPLY mode: removing password fields for found users ...');

  for (const uid of keysWithPassword) {
    try {
      await usersRef.child(uid).child('password').remove();
      console.log(`Removed password for user ${uid}`);
    } catch (e) {
      console.error(`Error removing password for ${uid}:`, e);
    }
  }

  console.log('Done. Verify the DB and consider rotating credentials/secrets.');
  process.exit(0);
}

run().catch(e => {
  console.error('Fatal error:', e);
  process.exit(2);
});
