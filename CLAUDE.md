# AI Assistant Instructions

## General

Be very critical and honest to what I say. **VERY IMPORTANT**

Don't forget to update the documentation when you change the code. **VERY IMPORTANT**

If you create a new file, also stage exactly this file to git, but not any other files that are not staged.

If you commit something, don't include that co-author hint.

Test coverage should be taken into consideration in the TDD loop.

Always check with the IDE MCP for warnings (make sure that you don't see just errors, but also warnings)
and all qualified names are replaced by imports (if possible). **VERY IMPORTANT**

## TDD

See `TDD.md` for Test-Driven Development practices that must be followed every time a new feature is added
or a bug is fixed (but not when just refactoring). The clean code principles from `CLEAN_CODE.md` are applied
automatically during the TDD refactor phase via a subagent. **VERY IMPORTANT**

## Clean Code

**VERY IMPORTANT**: If you do a refactoring (i.e. change the production code without affecting the tests)
or review existing code, then use a **Clean Code Review (Subagent)**:
Use the Task tool with `subagent_type=general-purpose` to spawn a clean code review subagent.
The subagent's prompt must instruct it to:

1. Read `CLEAN_CODE.md`
2. Read the implementation needing refactoring
3. Analyze the code against all clean code principles in priority order
   (naming, code smells, SOLID, method design, structure)
4. Return a prioritized list of specific, actionable refactoring suggestions
   Apply the returned suggestions, then ensure all tests continue to pass after each change.

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
