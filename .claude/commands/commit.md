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
- **Configuration changes**: All parameter adjustments in one config file = ONE line (e.g., "adjust Disruptor buffer size and timeout configuration")
- **Related refactorings**: Multiple refactorings for same purpose = ONE line (e.g., "refactor Connector interface to support multiple instruments")
- **Feature additions**: Main feature + its supporting changes = ONE line (e.g., "add OKX connector with WebSocket trade subscription")
- **Bug fixes**: Fix + related adjustments = ONE line
- **Connector updates**: Changes to same connector = ONE line
- **New class extraction**: Creating new class + refactoring to use it = TWO lines (one for creation, one for refactoring)

### Examples commit message
GOOD - Properly aggregated (3-5 lines):
```
- add Util class for timestamp and environment variable parsing
- refactor MarketDataProcessor to use NonDriftingTimer for candle synchronization
- adjust Disruptor buffer size and wait strategy configuration
- add volume tracking to CandleAggregator OHLCV calculation
- remove unused WebSocket ping/pong handlers in BinanceConnector
```

BAD - Too granular with duplicates (DO NOT DO THIS):
```
- add Util class for timestamp parsing utilities
- add Util class for environment variable parsing utilities
- refactor MarketDataProcessor to use NonDriftingTimer
- update CandleAggregator to receive timer events from NonDriftingTimer
- update Server to initialize NonDriftingTimer with processor list
- update Disruptor buffer size from 4096 to 8192
- update Disruptor wait strategy from blocking to yielding
- update Disruptor timeout from 1000ms to 500ms
- add volume tracking to CandleAggregator
- remove unused ping handler in BinanceConnector
- remove unused pong handler in BinanceConnector
```

### Input Format
Get GIT diff of uncommitted changes

### Task
1. Get git diff of staged and unstaged changes
2. Stage all unstaged changes with `git add`
3. Analyze changes and create aggregated commit message following guidelines above
4. IMPORTANT: Avoid duplicates - refactoring MarketDataProcessor already implies updating dependent components
5. IMPORTANT: Group all config parameter changes in one line (e.g., "adjust Disruptor configuration" not separate lines per parameter)
6. Create commit without asking for user confirmation