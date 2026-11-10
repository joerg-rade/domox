---
name: improve-junit-tests
description: Improve the readability, structure, and assertion coverage of JUnit 5 tests. Use when the user asks to refactor, clean up, or expand JUnit test suites.
---
# Improve JUnit Tests

A systematic guide to refactoring and enhancing JUnit 5 tests for better readability, maintainability, and high coverage.

## When to Use
- The user provides a JUnit test and wants to make it cleaner or more readable.
- The user wants to increase assertion coverage or add missing edge cases for a given class or method.
- Refactoring bulky test methods into parameterized tests, cleaner assertions (using AssertJ), or meaningful display names.

## Key Principles for Better JUnit Tests

### 1. Readability & Structure
- **Descriptive Naming:** Use clear test names using `@DisplayName` or descriptive method names (e.g., `givenValidUser_whenRegister_thenSuccess` or `should_return_error_when_input_is_null`).
- **Arrange-Act-Assert (AAA):** Clearly separate the setup (`given`/`arrange`), execution (`when`/`act`), and verification (`then`/`assert`) phases with blank lines or comments.
- **Fluent Assertions:** Prefer **AssertJ** (`assertThat(...)`) over native JUnit `assertEquals` or `assertTrue` for expressive, readable failure messages.

### 2. Coverage & Edge Cases
- **Happy Path:** Verify standard successful execution.
- **Boundary Conditions:** Test minimum, maximum, and threshold values (e.g., empty lists, zero, boundary integers).
- **Error & Exception Handling:** Test invalid inputs, nulls, and expected exceptions using `assertThrows()`.
- **Parameterized Tests:** Use `@ParameterizedTest` with `@ValueSource`, `@CsvSource`, or `@MethodSource` to test multiple inputs without code duplication.

## Refactoring Workflow

1. **Analyze:** Inspect the existing test snippet for boilerplate, weak assertions, or lack of boundary tests.
2. **Refactor Structure:** Apply AAA pattern, rename methods for clarity, and introduce `@DisplayName`.
3. **Upgrade Assertions:** Convert basic JUnit assertions to AssertJ for enhanced readability.
4. **Expand Coverage:** Suggest or write missing edge-case and parameterized tests.