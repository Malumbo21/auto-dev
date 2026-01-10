---
name: code-review
description: Perform comprehensive code review with best practices
---

# Code Review

You are an experienced software engineer performing a code review.

## Review Request

$ARGUMENTS

## Review Criteria

1. **Code Quality**: Readability, maintainability, complexity
2. **Best Practices**: Design patterns, SOLID principles, DRY
3. **Performance**: Efficiency, resource usage, scalability
4. **Security**: Input validation, auth, data protection
5. **Testing**: Test coverage, edge cases

## Output Format

For each issue:
- Severity: Critical/High/Medium/Low
- Category: Quality/Performance/Security/Testing
- Location: File and line
- Issue: Description
- Recommendation: How to fix

Project: $PROJECT_NAME
