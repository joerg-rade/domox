# Environment Setup Rules

Whenever executing terminal commands that require Java, Maven, or SDKMAN binaries:
1. Always source SDKMAN before running Maven or Java commands.
2. Prefer this shell-execution scaffold that works whether or not `.sdkmanrc` exists:
   ```shell
   source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env || true && mvn <command>
   ```
   The `|| true` prevents failure when the repo does not have a `.sdkmanrc` file.
   Note: there is NO `.sdkmanrc` in this repo, so `sdk env` always falls back to the SDKMAN
   default JDK — do not assume a pinned version.

# Repo Map (navigation)

Before searching the codebase, jump straight to the right steer/doc for common tasks:

- **Causeway annotations & conventions** (e.g. `@DomainObject(editing = Editing.ENABLED)`) →
  `memory-bank/technical/CAUSEWAY.md`
- **Module layout / build** → `memory-bank/technical/MAVEN.md`
- **Persistence / schema / JPA field migration** (TABLE_PER_CLASS trap, `create-tables.sql`,
  drift check) → `memory-bank/technical/SCHEMA.md`
- **Candidate-review pipeline over MCP** → `mcp-review-pipeline.md`
- **Domain-modeling (CRC) extraction rules** → `crc_domain_modeling_guide.md`
- **Run the app & confirm boot** → start with a teed log so failures are debuggable:
  `nohup mvn -pl domox-webapp spring-boot:run > /tmp/domox-boot.log 2>&1 &`, then
  `tail -f /tmp/domox-boot.log`.  Liveness: `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/`
  (expect 200); MCP endpoint: `curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/mcp`
  (expect 200; 404 = the `spring.ai.mcp.server.protocol` key issue, see `mcp-review-pipeline.md`).

Quick checks after an entity change: run `bash scripts/check-schema-drift.sh`, then
`mvn -B -pl domox-domain -am test`.

# Shell Execution Formatting Rules

When preparing terminal execution requests:
1. NEVER chain multiple long commands on a single line using `&&`.
2. Format chained commands using multiline line-continuations (`\`) with each sub-command on its own indented line.
3. Keep shell commands concise and visually structured for easy review.

Example preference (use line continuations for long command chains):
source "$HOME/.sdkman/bin/sdkman-init.sh" \
  && sdk env || true \
  && mvn clean install

# Terminal Command Guidelines

## Allowed Commands
You are pre-approved to run the following commands automatically when needed:

### Exact commands (first-token match):
- 'source'
- 'cd'
- 'mvn'
- 'java'
- 'docker'
- 'sed'
- 'sdk'
- 'which', 'find', 'ls', 'pwd', 'cat', 'echo', 'grep', 'tail', 'head', 'less', 'more', 'cp', 'mv', 'mkdir', 'rmdir'

### Chained patterns:
Any command whose first whitespace-delimited token is one of the exact commands above is pre-approved, whether standalone or chained with `&&`, `||`, `;`, `|`, and/or `2>&1`. Piping through `grep`, `sed`, `tail`, `head` for output filtering or line-range extraction is included. Additional arguments and flags may follow.

### Examples of pre-approved commands (all variants):
- source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn compile -pl domox-domain -q 2>&1
- source "$HOME/.sdkman/bin/sdkman-init.sh" && cd /home/jrade/projects/domox && mvn compile -pl domox-domain 2>&1 | tail -15
- source "$HOME/.sdkman/bin/sdkman-init.sh" && cd /home/jrade/projects/domox && mvn test -pl domox-domain 2>&1 | tail -60
- grep -n "ActionCdd" /home/jrade/projects/domox/domox-domain/src/main/java/domox/dom/rules/RuleMatches.java
- cat -n /home/jrade/projects/domox/domox-domain/src/test/java/domox/dom/rules/RuleMatchesTest.java | sed -n '83,100p'
- cat -n /home/jrade/projects/domox/domox-domain/src/main/java/domox/dom/rules/RuleMatches.java | sed -n '100,218p'
- docker exec domox-db psql -U postgres -d postgres -c "SELECT ..."
- docker ps --format '{{.Names}} {{.Image}} {{.Status}}' 2>/dev/null | head -5
- sdk current java 2>&1 | head -5

## Strictly Forbidden / Require Manual Confirmation
DO NOT run these commands without explicit approval or if there is any doubt:
- Any `rm` or `git reset --hard` command.
- 'git push' or any commands that modify remote repositories.
- 'git commit'
- Any dependency modification commands like `npm install`, `mvn dependency:...`
- Any command that modifies system configurations outside the workspace.