# Case Study Scenarios to discuss

## Scenario 1: Cost Allocation and Tracking
**Situation**: The company needs to track and allocate costs accurately across different Warehouses and Stores. The costs include labor, inventory, transportation, and overhead expenses.

**Task**: Discuss the challenges in accurately tracking and allocating costs in a fulfillment environment. Think about what are important considerations for this, what are previous experiences that you have you could related to this problem and elaborate some questions and considerations

**Questions you may have and considerations:**
```txt
1. Business objective: give finance and operations a trustworthy, auditable answer to "what does
   it cost to fulfil an order/unit/shipment", so decisions rest on real numbers, not estimates.

2. Stakeholders/information: Finance (existing cost-centre structure, current allocation
   practice), Ops (what actually drives labor/handling cost per warehouse), Logistics (carrier
   contracts - per-shipment vs. blanket), IT/Procurement (existing systems already holding part of
   the truth: WMS, TMS, payroll, ERP).

3. Key questions: What's the smallest costable unit - order, unit, shipment? Which costs are
   direct vs. indirect (needing an allocation driver: per unit, per m², per labor-hour)? Do we need
   committed/accrued cost, not just actuals? Multi-currency - convert at transaction time or at
   reporting time? Can allocation rules change over time and still reproduce a past report exactly
   as it looked then?

4. Challenges/risks: indirect allocation is a policy choice, not a fact - getting Finance and Ops
   to agree on (and periodically revisit) the driver is the hard part. Unversioned rule changes
   silently drift historical reports. Allocated cost will diverge from posted actuals (Scenario 3)
   without a defined reconciliation cadence. Every number needs to trace back to its source
   transaction and rule version, or an audit question has no answer.

5. Approach: a canonical cost model with explicit dimensions (warehouse, store, product,
   order/shipment, cost centre, period, currency); direct costs attached to their transaction,
   indirect costs allocated via named, versioned drivers; allocated/estimated cost kept distinct
   from posted actuals at the data-model level.

6. Outcomes/KPIs: cost per order/unit/shipment, trended and drillable to source; allocation
   variance (allocated vs. actual) trending toward zero; time-to-traceability for any cost line.

7. Assumptions/trade-offs: assumes Finance and Ops can agree on drivers - if not, the tool can
   surface the disagreement but not resolve it. A finer costing grain is more useful but
   proportionally more expensive to instrument; I'd start coarser and refine once trusted.
```

## Scenario 2: Cost Optimization Strategies
**Situation**: The company wants to identify and implement cost optimization strategies for its fulfillment operations. The goal is to reduce overall costs without compromising service quality.

**Task**: Discuss potential cost optimization strategies for fulfillment operations and expected outcomes from that. How would you identify, prioritize and implement these strategies?

**Questions you may have and considerations:**
```txt
1. Business objective: reduce cost per order/unit measurably, without regressing the
   service-quality metrics (on-time delivery, accuracy, availability) cost-cutting can quietly erode.

2. Stakeholders/information: Ops leadership (known pain points), the Scenario 1 cost model
   (optimizing without a trusted baseline is guesswork), service-quality owners (the guardrails
   that must not regress), Finance (capital availability, payback expectations).

3. Key questions: where does the cost model show the largest, most persistent variance between
   comparable locations? For each candidate: expected saving, confidence, effort, and risk to
   service if wrong? Which strategies are reversible (a single-warehouse pilot) vs. structural
   (closing a warehouse) - the latter needs a much higher confidence bar.

4. Challenges/risks: labor/slotting changes directly touch pick accuracy and throughput. Transport
   consolidation trades delivery time for efficiency - the trade-off needs measuring, not
   assuming away. Automation business cases span budget cycles (Scenario 4). Running several
   optimizations at once makes it hard to attribute an observed saving to any one of them.

5. Approach: prioritize by expected benefit / (cost x risk), weighted by confidence. Every
   candidate (labor, slotting, inventory balancing, transport/route consolidation, energy,
   damage/returns, automation) scored the same way. Roll out via controlled pilots with the
   service-quality guardrails wired in as automatic stop conditions before any network-wide commit.

6. Outcomes/KPIs: realised savings vs. forecast, at the same grain as the pilot (no
   network-wide extrapolation presented as fact before confirmed at scale); guardrail dashboard
   tracked alongside the cost KPI for every pilot; a register of "tried, worked/didn't, why".

7. Assumptions/trade-offs: assumes the Scenario 1 cost model is trusted enough to prioritize
   against. I'd bias toward fewer, well-instrumented pilots over a wide simultaneous rollout - an
   unmeasured service regression is expensive to unwind and to earn trust back from.
```

## Scenario 3: Integration with Financial Systems
**Situation**: The Cost Control Tool needs to integrate with existing financial systems to ensure accurate and timely cost data. The integration should support real-time data synchronization and reporting.

**Task**: Discuss the importance of integrating the Cost Control Tool with financial systems. What benefits the company would have from that and how would you ensure seamless integration and data synchronization?

**Questions you may have and considerations:**
```txt
1. Business objective: give Finance a cost feed they can post and audit without manual re-entry or
   a standing reconciliation burden between what operations sees and what the books say.

2. Stakeholders/information: Finance/ERP (system of record, integration surface, close calendar),
   Security/compliance (financial data carries stricter audit/access obligations), Ops (what
   "near-real-time" actually needs to mean for their decisions vs. Finance's periodic posting).

3. Key questions: is the ERP system of record with the tool as an upstream estimator, or does the
   tool originate journal entries itself? What's the actual freshness SLA per consumer - conflating
   operational near-real-time needs with Finance's periodic close leads to over- or under-building.
   What happens when a downstream system is unavailable - queue and retry, never silently drop?
   How do we correct a record already sent - reversing entry, replay, manual ERP adjustment?

4. Challenges/risks: "real-time" without an explicit per-consumer SLA wastes effort on the wrong
   infrastructure. Retries risk double-counting without idempotency. The tool's cost model and the
   ERP's chart of accounts evolve independently - unversioned mappings silently misclassify cost.
   Mismatches will happen and need a visible quarantine queue, not a log line nobody watches.

5. Approach: canonical, versioned cost events - API/event stream for real-time consumers, batch
   export for the ERP's cadence. Idempotent delivery (stable event IDs, upsert semantics).
   Versioned field-level mappings to the chart of accounts, a reconciliation job comparing sent
   vs. posted, a quarantine queue with replay-from-source. Delivery-lag/failure observability and
   a full audit trail.

6. Outcomes/KPIs: reconciliation match rate trending toward 100% with a shrinking, actively-worked
   quarantine queue; delivery freshness against the agreed SLA; reduced manual reconciliation
   effort for Finance - usually the clearest ROI case for this integration.

7. Assumptions/trade-offs: assumes the ERP stays system of record for posted actuals. Near-
   real-time operational estimates and posted actuals are kept as two distinct, clearly-labelled
   things throughout - collapsing them is faster to build but erodes trust the first time they
   disagree, which they will, by timing alone.
```

## Scenario 4: Budgeting and Forecasting
**Situation**: The company needs to develop budgeting and forecasting capabilities for its fulfillment operations. The goal is to predict future costs and allocate resources effectively.

**Task**: Discuss the importance of budgeting and forecasting in fulfillment operations and what would you take into account designing a system to support accurate budgeting and forecasting?

**Questions you may have and considerations:**
```txt
1. Business objective: let the business commit to a cost plan with confidence, catch deviations
   early enough to act on, and allocate resources against a plan grounded in the same cost model
   used to track actuals.

2. Stakeholders/information: FP&A (budget cadence, approval workflow, existing methodology),
   Ops (demand drivers already tracked: seasonality, promotions, known network changes),
   Procurement/HR (labor rates, inflation, fuel/energy outlook - inputs from outside the system).

3. Key questions: forecast horizon/cadence - annual with monthly re-forecast, rolling 12-month,
   both? Which costs are fixed, variable, or one-time (a warehouse replacement's capex, Scenario
   5)? Where do demand-driver forecasts already live vs. needing to be built here? How are
   assumptions (fuel price, inflation) versioned so a forecast is reproducible? Do we need
   scenario planning (base/upside/downside), and who approves which becomes "the" budget?

4. Challenges/risks: unversioned assumptions make forecasts silently wrong until the variance is
   large. Currency/inflation exposure compounds over a long horizon without periodic re-basing.
   Too-heavy approval workflows slow re-forecasting exactly when it's needed most; too-light
   erodes budget discipline. A forecast too tightly coupled to trailing actuals is really just
   "last period plus a growth rate" - insufficient once a known network change should shift it.

5. Approach: forecast built from the same dimensions as actuals, so variance analysis is a direct
   comparison. Versioned assumption sets (demand growth, labor rate, inflation, fuel/energy, FX)
   a forecast run references explicitly. Rolling re-forecast cadence, lightweight approval for
   routine updates and heavier for base-budget commitments, explicit scenario support for major
   decisions.

6. Outcomes/KPIs: forecast accuracy (forecast vs. actual) trended over time; variance explained
   (traces to a named assumption) vs. unexplained; time from a demand/cost shock observed to a
   re-forecast reflecting it.

7. Assumptions/trade-offs: assumes demand-driver forecasts come from elsewhere (demand planning),
   not originated here. A finer-grained, more frequent model costs more analyst time to maintain -
   I'd start at the existing budget cadence and increase frequency only where variance shows it's
   warranted.
```

## Scenario 5: Cost Control in Warehouse Replacement
**Situation**: The company is planning to replace an existing Warehouse with a new one. The new Warehouse will reuse the Business Unit Code of the old Warehouse. The old Warehouse will be archived, but its cost history must be preserved.

**Task**: Discuss the cost control aspects of replacing a Warehouse. Why is it important to preserve cost history and how this relates to keeping the new Warehouse operation within budget?

**Questions you may have and considerations:**
```txt
1. Business objective: execute the replacement without losing the cost history needed to prove
   its business case and to hold the new warehouse to a budget informed by the old one's actuals,
   not a guess.

2. Stakeholders/information: Finance (approved capex/opex budget, how it wants replacement cost
   tracked vs. budget/forecast/commitments), Ops (old warehouse's cost history, migration/
   dual-running plan), HR/facilities/IT (training, lease/construction/equipment/IT costs).

3. Key questions: the business unit code is reused by design, but it identifies "the role", not
   "the physical warehouse that incurred a given cost" - without a separate immutable identity per
   warehouse version, "this warehouse's cost" is ambiguous once both old and new have used the same
   code. Is the transition a hard cutover (matching the domain's archive-on-replace) or a
   dual-running overlap with simultaneous cost (double rent, duplicated labor) that the domain's
   instantaneous archive/replace doesn't itself capture? Is the new budget set fresh, or as
   trailing actuals plus known deltas - the latter needs the old warehouse's history queryable.
   Which replacement costs are one-time (lease deposit, construction, equipment, IT, training) vs.
   ongoing, and how are actuals/commitments tracked against budget as the project proceeds?

4. Challenges/risks: without preserved, attributable cost history, the replacement's ROI can't be
   demonstrated after the fact. Reusing a business unit code without an immutable per-version
   identity risks reports silently blending old- and new-warehouse cost. Dual-running costs are
   easy to under-budget since they're incremental to both warehouses at once. Replacement projects
   commonly slip on cost/timeline; without a tracked contingency line, overruns surface as a
   budget miss instead of a managed risk.

5. Approach: model each warehouse with an immutable warehouse-version identity independent of the
   business-unit code it carries, plus valid-from/valid-to, status, and an explicit
   predecessor/successor link - the business unit code stays the stable business identifier
   (matching the archive-and-insert pattern already implemented for replacement), while cost
   history attaches to the version. Track one-time replacement costs as their own budget line,
   separate from steady-state opex. Track approved budget vs. forecast vs. committed vs. actual
   for the project, with an explicit contingency line.

6. Outcomes/KPIs: new warehouse's steady-state cost per unit/order vs. the old warehouse's
   trailing actuals - the direct evidence of benefit; project actual vs. approved budget by cost
   category, and contingency utilisation; a post-replacement benefits-realisation review at a
   fixed point (e.g. 90/180 days) comparing projected to observed savings.

7. Assumptions/trade-offs: assumes Finance wants replacement cost tracked as its own project
   rather than folded into ongoing opex - more rigorous, more upfront structure. An immutable
   warehouse-version identity is added modelling complexity beyond the business-unit code alone;
   worth it specifically because cost history and audit traceability are the stated requirement
   here.
```

## Instructions for Candidates
Before starting the case study, read the [BRIEFING.md](BRIEFING.md) to quickly understand the domain, entities, business rules, and other relevant details.

**Analyze the Scenarios**: Carefully analyze each scenario and consider the tasks provided. To make informed decisions about the project's scope and ensure valuable outcomes, what key information would you seek to gather before defining the boundaries of the work? Your goal is to bridge technical aspects with business value, bringing a high level discussion; no need to deep dive.
