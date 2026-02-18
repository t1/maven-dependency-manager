# AI Assistant Instructions

## General

**VERY IMPORTANT** Be very critical and honest to what I say. And when I ask a question,
it's just a question, not a suggestion.
**Do NOT use local/private auto memory.** Store all learnings and conventions in this file (or other project files),
so they are shared with everyone working on the project.

Don't forget to update the documentation when you change the code. **VERY IMPORTANT**

If you create a new file, also stage exactly this file to git, but not any other files that are not staged.

If you commit something, don't include that co-author hint.

Test coverage should be taken into consideration in the TDD loop.

Always check with the IDE MCP for warnings (make sure that you don't see just errors, but also warnings)
and all qualified names are replaced by imports (if possible). **VERY IMPORTANT**

## TDD & Clean Code

This project uses the **tdder** plugin for TDD and Clean Code practices. **VERY IMPORTANT**

- TDD practices (Red-Green-Refactor, baby steps, guessing game) are enforced by the plugin's `tdd` skill
- Clean Code principles and the Clean Code Reviewer subagent are provided by the plugin's `clean-code` skill
- Java and Maven conventions are provided by the plugin's `java` and `maven` skills

See `TDD.md` and `CLEAN_CODE.md` for pointers to the plugin.

## Documentation Strategy

**IMPORTANT**: This project uses `package-info.java` files for package-level documentation and architecture.

When working with code:

- **Always read the relevant `package-info.java` files** to understand the architecture, patterns, and conventions for
  that package
- Use `package-info.java` to understand package responsibilities, dependencies, and relationships
- See `README.md` for project overview, tech stack, setup instructions, development guidelines, and coding style
    - that Code Style is **VERY IMPORTANT**
- For package-specific details, refer to the co-located `package-info.java` files

## Key Resources

- **README.md** - Project documentation, setup, testing, coding conventions
- **package-info.java files** - Package-level architecture and patterns
    - `com.ionos.rocket.backtron.domain` - Domain layer overview
    - Individual domain packages (profiles, sources, backups, restores, credentials)
