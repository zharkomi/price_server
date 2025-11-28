## Summarize git changes and make a commit

You are an expert software developer tasked with writing clear, concise, and informative commit messages. Analyze the provided code changes and generate an appropriate commit message following these guidelines:

### Commit Message Structure
Use the conventional commit format. Each line in comment must follow it!
```
- <description>
```
Before commit make sure message following above guidelines.

### Guidelines
1. **CRITICAL: Aggregate related changes into ONE line** - Multiple parameter adjustments, related refactorings, or changes to the same feature MUST be combined
2. **Keep it concise** - Aim for 3-7 lines maximum for most commits
3. **Use imperative mood** ("add" not "added" or "adds")
4. **Focus on what and why, not implementation details**
5. **Ignore hardcoded IDs and run parameters in scripts** - These are temporary values not worth mentioning

### Aggregation Rules (CRITICAL)
- **Configuration changes**: All parameter adjustments in one config file = ONE line (e.g., "adjust SAC hyperparameters in Bertoac configuration")
- **Related refactorings**: Multiple refactorings for same purpose = ONE line (e.g., "refactor AgentParameters to use BufferParameters")
- **Feature additions**: Main feature + its supporting changes = ONE line (e.g., "add OAuth2 login integration with unit tests")
- **Bug fixes**: Fix + related adjustments = ONE line
- **Tool updates**: Changes to same tool/script = ONE line
- **New class extraction**: Creating new class + refactoring to use it = TWO lines (one for creation, one for refactoring)

### Examples commit message
GOOD - Properly aggregated (3-5 lines):
```
- add BufferParameters class to encapsulate replay buffer configuration
- refactor AgentParameters to use BufferParameters
- adjust SAC hyperparameters in Bertoac configuration
- add percentile tracking to training results logging
- remove unused chart configuration in Ternanda strategy
```

BAD - Too granular with duplicates (DO NOT DO THIS):
```
- add BufferParameters class to encapsulate replay buffer configuration
- refactor AgentParameters to use BufferParameters instead of direct fields
- update ReplayBuffer to reference buffer_parameters for sampler configuration
- update Bertoac training iterations from 1200 to 1800
- update Bertoac max_times_sampled from 4 to 3
- update Bertoac replay_priority_exponent from 0.35 to 0.7
- add max_reward and max_pnl percentile logging to training results
- remove unused chart configuration in Ternanda strategy
```

### Input Format
Get GIT diff of uncommitted changes

### Task
1. Get git diff of staged and unstaged changes
2. Stage all unstaged changes with `git add`
3. Analyze changes and create aggregated commit message following guidelines above
4. IMPORTANT: Avoid duplicates - refactoring AgentParameters already implies updating ReplayBuffer usage
5. IMPORTANT: Group all config parameter changes in one line (e.g., "adjust SAC hyperparameters" not separate lines per parameter)
6. Create commit without asking for user confirmation