// Runs archify validate and prints only the codes a repair round should act on.
// Usage: node check.mjs <type> <spec.json> [--all]
import { execFileSync } from 'node:child_process'

const [type, spec, ...rest] = process.argv.slice(2)
const all = rest.includes('--all')
const bin =
  'C:/Users/LYQ10/.dsh/profiles/web/node_modules/@tt-a1i/archify-dsh/skills/archify/bin/archify.mjs'
const repoRoot = 'D:/Repo/labs-archiveassistant-iOS'

let raw
try {
  raw = execFileSync(
    process.execPath,
    [bin, 'validate', type, spec, '--quality', 'showcase', '--repo-root', repoRoot, '--json'],
    { encoding: 'utf8' }
  )
} catch (error) {
  raw = (error.stdout || '') + (error.stderr || '')
}

let parsed
try {
  parsed = JSON.parse(raw)
} catch {
  console.log('NON-JSON OUTPUT:')
  console.log(raw.slice(0, 2000))
  process.exit(2)
}

console.log(`ok=${parsed.ok} stage=${parsed.stage ?? ''} diagnostics=${(parsed.diagnostics || []).length}`)
console.log('checks: ' + ((parsed.checks || []).map((c) => `${c.status}${c.name ? ':' + c.name : ''}`).join('  ') || 'n/a'))

const codes = {}
for (const d of parsed.diagnostics || []) codes[d.code] = (codes[d.code] || 0) + 1
for (const [code, n] of Object.entries(codes).sort()) console.log(`  ${String(n).padStart(3)}  ${code}`)

const shown = all ? parsed.diagnostics || [] : (parsed.diagnostics || []).filter((d) => !String(d.code).startsWith('layout/'))
console.log(`--- ${all ? 'all' : 'structural only'} (${shown.length}) ---`)
for (const d of shown.slice(0, 14)) {
  console.log(`[${d.code}] ${String(d.message).replace(/\s+/g, ' ').slice(0, 190)}`)
}
process.exit(parsed.ok ? 0 : 1)
