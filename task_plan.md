# Task Plan: Fix sbt load warnings and create PR

## Goal
Fix all sbt load warnings in the Pekko project and create a PR to the main repository.

## Acceptance Criteria
- [ ] All sbt load warnings are identified
- [ ] All warnings are fixed
- [ ] sbt loads without any warnings
- [ ] PR is created with proper description

## Phases

### Phase 1: Identify Warnings
- [x] Run sbt load to capture all warnings
- [x] Document each warning with file location and description
- [x] Categorize warnings by type

### Phase 2: Fix Warnings
- [x] Fix each warning systematically
- [x] Verify fixes don't break functionality
- [x] Run sbt load again to confirm warnings are gone

### Phase 3: Create PR
- [x] Create new branch
- [x] Commit changes with proper message
- [x] Push branch and create PR
- [x] Follow PR template from AGENTS.md

## Current Status
- Phase: 3 (Create PR) - Completed
- Started: 2026-06-01
- Last Updated: 2026-06-01
- PR Created: https://github.com/apache/pekko/pull/3029
- Branch: fix/sbt-load-warnings-clean

## Warnings Identified
Total: 165 unused keys

### Warning Categories:
1. `projectInfoVersion` - defined in PekkoBuild.scala:48 and :118
2. `Pr-validation / fork` - defined in ValidatePullRequest.scala:70
3. `logManager` - defined in AddLogTimestamps.scala:32
4. `test / javacOptions` - defined in PekkoBuild.scala:305
5. `Javaunidoc / unidoc / unidocProjectFilter` - defined in Doc.scala:153

### Files to Examine:
- /Users/hepin/IdeaProjects/pekko/project/PekkoBuild.scala
- /Users/hepin/IdeaProjects/pekko/project/ValidatePullRequest.scala
- /Users/hepin/IdeaProjects/pekko/project/AddLogTimestamps.scala
- /Users/hepin/IdeaProjects/pekko/project/Doc.scala

### Analysis:
These warnings are from sbt's `lintUnused` check, which reports settings that are defined but not used by other settings/tasks. The settings are:

1. `projectInfoVersion` - Used for linking to API docs (overwrites `project-info.version`)
2. `Pr-validation / fork` - Used to make PR validation fork like regular test running
3. `logManager` - Used to add timestamps to log output when enabled
4. `test / javacOptions` - Used to disable doclint for tests
5. `Javaunidoc / unidoc / unidocProjectFilter` - Used to filter projects for unidoc generation

These settings are actually useful but sbt's lint check doesn't recognize their usage. The recommended solution is to add them to `Global / excludeLintKeys`.