You are acting as a Principal Software Architect and AI Engineering Lead.

Your job is NOT to implement features.

Your job is to transform this repository into a complete AI-native engineering workspace that supports specification-driven development.

==============================================================================
OBJECTIVE
==============================================================================

For EVERY service in this repository that contains a specification package(as specified in the spec directory), generate the complete prompt workflow required to execute every implementation task independently.

The generated prompts must follow the engineering workflow described below.

DO NOT invent a new workflow.

Replicate it consistently.

==============================================================================
ENGINEERING PIPELINE
==============================================================================

Every implementation task SHALL execute through the following phases.

Phase 0
Repository Understanding

↓

Phase 1
Specification Extraction

↓

Phase 2
Task Implementation Brief (TIB)

↓

Phase 3
Design Challenge

↓

Phase 4
Freeze Task Brief

↓

Phase 5
Implementation Plan

↓

Phase 6
Implementation

↓

Phase 7
Self Review

↓

Phase 8
Independent Code Review

↓

Phase 9
Review Resolution

↓

Phase 10
Test Generation

↓

Phase 11
Test Review

↓

Phase 12
Specification Verification

↓

Phase 13
PR / Commit Preparation

Each phase produces exactly ONE artifact.

Each phase consumes the artifact from the previous phase.

No phase may skip an earlier artifact.

======================================================================================================
Add a comment of preffered MODEL useage for each generated prompt. Allowed options are listed below 
=======================================================================================================

Use these models unless explicitly overridden.

Phase 0
Claude Sonnet

Phase 1
Claude Sonnet

Phase 2
Claude Sonnet

Phase 3
Kimi 2.7

Phase 4
Human Approval

Phase 5
Claude Sonnet

Phase 6
Claude Sonnet

Phase 7
Claude Sonnet

Phase 8
Kimi 2.7

Phase 9
Human Approval

Phase 10
Claude Sonnet

Phase 11
Kimi 2.7

Phase 12
Claude Sonnet

Phase 13
Claude Sonnet

Do NOT use Opus/Fable unless a prompt explicitly requires architectural reasoning beyond the specification.

==============================================================================
DISCOVERY
==============================================================================

Automatically discover

- services

- specification packages

- requirements

- design documents

- tasks

- contracts

- architecture documents

- coding conventions

Do not assume filenames.

Infer relationships where appropriate.

==============================================================================
TASK DISCOVERY
==============================================================================

For every discovered task

Example

Task 11

Task 12

Task 13

...

Generate a COMPLETE prompt set.

Every task receives its own prompts.

No shared implementation prompts.

==============================================================================
OUTPUT STRUCTURE
==============================================================================

For every service create

.ai/

    prompts/

        <service-name>/

            T01/

            T02/

            ...

Example

.ai/prompts/auth/T11/

Inside every task folder generate

00-repository-understanding.md

01-specification-extraction.md

02-task-implementation-brief.md

03-design-challenge.md

04-freeze-task.md

05-implementation-plan.md

06-implementation.md

07-self-review.md

08-independent-review.md

09-review-resolution.md

10-test-generation.md

11-test-review.md

12-specification-verification.md

13-pr-preparation.md

Also generate

README.md

that explains

Purpose

Inputs

Outputs

Expected artifact

Responsible agent

When to stop

==============================================================================
PROMPT REQUIREMENTS
==============================================================================

Every generated prompt MUST

reference the task number

reference the relevant requirement IDs

reference the relevant locked decisions

reference the relevant acceptance criteria

reference relevant tests

reference relevant contracts

limit the implementation scope to the current task

explicitly forbid unrelated refactoring

explicitly forbid speculative improvements

explicitly stop after the requested deliverable

Every prompt should be immediately usable without editing.

==============================================================================
TASK IMPLEMENTATION BRIEF
==============================================================================

The Phase 2 prompt SHALL produce a Task Implementation Brief using this structure

Task

Purpose

Scope

Business Rules

Locked Decisions

Dependencies

Inputs

Outputs

State Changes

Files to Create

Files to Modify

Files NOT to Modify

Acceptance Criteria

Required Tests

Constraints

Open Questions

Nothing else.

==============================================================================
QUALITY REQUIREMENTS
==============================================================================

Prompts should

minimize token usage

avoid repetition

reuse requirement IDs

reuse task IDs

avoid copying entire specifications

reference documents instead

Prompts should assume Claude Code has repository access.

==============================================================================
FINAL STEP
==============================================================================

After generating every prompt

Generate

.ai/WORKFLOW.md

which documents

the engineering workflow

artifact chain

model responsibilities

directory layout

how engineers should execute a task

common mistakes

cost optimization guidance

Then generate

.ai/README.md

that explains how a new engineer or AI agent should use the system.

==============================================================================
IMPORTANT
==============================================================================

Do NOT implement any application code.

Do NOT modify business logic.

Do NOT change the specifications.

Only generate the AI engineering framework.

Treat this as production-quality tooling that will be used repeatedly throughout the life of the repository.

==============================================================================
REFERENCE IMPLEMENTATION (ADVISORY ONLY)
==============================================================================

A reference example exists at:

/Users/macbookpro/personal/crypto-dispute/spec/auth-service/artifacts

and all of its subdirectories.

These files represent an example implementation of the engineering workflow for one specification package.

Their purpose is ONLY to demonstrate the expected, structure, naming conventions, level of detail, and organization of the generated prompts. The quality of work expected should still be far bettter than what we have here. 

Treat them strictly as reference material. You are not under compulsion to follow this strictly.

DO NOT:

- limit generation to the auth-service
- copy the example verbatim
- assume the example is complete
- assume the example defines the repository structure
- assume the example contains the latest workflow
- skip generation because a similar artifact already exists

Instead:

1. Inspect the reference artifacts to understand the desired output quality.
2. Apply the engineering workflow defined in this prompt as the authoritative specification.
3. Discover EVERY service in the repository.
4. Discover EVERY specification package.
5. Discover EVERY implementation task.
6. Generate the complete prompt workflow for EVERY task of EVERY service.
7. Where the reference example and this prompt differ, THIS PROMPT ALWAYS TAKES PRECEDENCE.

The auth-service example is advisory only.

The objective is a complete repository-wide AI engineering framework, not an auth-service implementation.