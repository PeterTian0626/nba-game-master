# Trade Evaluation Framework

## Purpose

This document defines the default reasoning framework for evaluating an NBA trade from one team’s perspective.

Use this document when the user asks whether a trade was good, bad, risky, smart, short-sighted, fair, or damaging for a specific team.

## Core Evaluation Principle

A trade should not be judged only by the best player moved in the deal. A good trade evaluation weighs:

- Assets received
- Assets sent
- Short-term basketball impact
- Long-term roster-building impact
- Basketball fit
- Contract and cap implications
- Opportunity cost
- Risk and uncertainty
- Team timeline

## Assets Received

Consider all meaningful incoming assets:

- Players
- Draft picks
- Pick swaps
- Young prospects
- Expiring contracts
- Trade exceptions
- Salary relief
- Roster flexibility
- Open roster spots

A player is not just an abstract talent asset. Evaluate how his role, contract, age, injury profile, and skill set fit the receiving team.

## Assets Sent

Consider all meaningful outgoing assets:

- Rotation players
- Star players
- Draft picks
- Pick swaps
- Prospects
- Matching salary
- Expiring contracts
- Flexible contracts
- Defensive specialists
- Shooters
- Depth pieces

Assets sent away should be evaluated by replacement cost, not just name value. Losing multiple useful role players can hurt a contender even if the incoming player has higher individual talent.

## Short-Term Impact

Short-term impact should answer:

- Does the trade improve the current rotation?
- Does it raise the team’s playoff ceiling?
- Does it solve a real weakness?
- Does it create a new weakness?
- Does it improve regular-season floor but reduce playoff reliability?
- Does it make the team more dependent on health or specific lineups?

Common short-term impact categories:

- Shot creation
- Spacing
- Defense
- Rebounding
- Bench depth
- Closing lineup quality
- Transition offense
- Half-court offense
- Playoff matchup flexibility

## Long-Term Impact

Long-term impact should answer:

- Does the trade align with the team’s competitive timeline?
- Did the team preserve future flexibility?
- Did the team spend draft capital responsibly?
- Did the team take on aging or declining players?
- Did the team create a hard-to-move contract problem?
- Did the team improve or weaken its asset base?

A trade can be reasonable for a contender but poor for a rebuilding team, or vice versa.

## Basketball Fit

Basketball fit should evaluate how the incoming player or assets interact with the team’s existing core.

Key questions:

- Does the player need the ball to be valuable?
- Can the player help without high usage?
- Does the player improve spacing?
- Does the player protect or attack the rim?
- Does the player defend a needed position?
- Can the player close playoff games?
- Does the player overlap with existing stars?
- Does the player make the team easier or harder to guard?
- Does the player make the team easier or harder to scheme against in the playoffs?

Fit is especially important for teams built around superstars. Complementary skills can matter more than raw individual talent.

## Cap or Contract Impact

Cap or contract impact should evaluate:

- Salary size
- Contract length
- Player option or team option status
- Luxury tax pressure
- Apron pressure
- Future trade flexibility
- Salary matching usefulness
- Negative-contract risk
- Extension risk
- Opportunity cost of using matching salary

Large contracts are not automatically bad. They become risky when the player’s on-court value, age curve, fit, or tradability does not justify the salary.

## Main Risks

Common trade risks include:

- Injury risk
- Poor shooting fit
- Defensive decline
- Age-related decline
- Role overlap
- Loss of depth
- Overpaying draft capital
- Reduced cap flexibility
- Bad playoff scalability
- Locker-room or role uncertainty
- Depending too much on optimistic development

## Team Grade

The team grade should be from the selected team’s perspective only.

Suggested grading scale:

- A: Strong value, clear fit, good risk management
- B: Positive move with manageable concerns
- C: Defensible but mixed or uncertain
- D: Poor value, poor fit, or high opportunity cost
- F: Severe mistake with major predictable downside

The grade should reflect both process and outcome when evaluating a historical trade. For current or hypothetical trades, the grade should include more uncertainty.

## Output Mapping

When generating structured output, map the reasoning to this record:

```java
public record TeamTradeEvaluation(
        String teamName,
        List<String> assetsReceived,
        List<String> assetsSent,
        String shortTermImpact,
        String longTermImpact,
        String basketballFit,
        String capOrContractImpact,
        List<String> mainRisks,
        String teamGrade
) {}
```

## Style Guidance

Be concise, analytical, and basketball-specific.

Avoid vague statements such as “it depends” unless followed by the exact factors that create uncertainty.

Do not invent trade details. If assets are uncertain or missing, state that the trade package is unclear.
