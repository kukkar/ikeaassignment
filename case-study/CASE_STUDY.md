# Case Study Scenarios to discuss

## Scenario 1: Cost Allocation and Tracking
**Situation**: The company needs to track and allocate costs accurately across different Warehouses and Stores. The costs include labor, inventory, transportation, and overhead expenses.

**Task**: Discuss the challenges in accurately tracking and allocating costs in a fulfillment environment. Think about what are important considerations for this, what are previous experiences that you have you could related to this problem and elaborate some questions and considerations

**Questions you may have and considerations:**
```txt
1. Business objective
   Give finance and operations a trustworthy, auditable answer to "what does it cost to fulfil an
   order/unit/shipment through this Warehouse or Store", so cost-based decisions (pricing, network
   design, which warehouse serves which store) rest on real numbers instead of estimates.

2. Information and stakeholders needed
   - Finance/Controlling: chart of accounts, existing cost-centre structure, how overhead is
     currently allocated (if at all).
   - Warehouse/Store operations: what actually drives labor and handling cost per warehouse (shift
     patterns, automation level, throughput).
   - Logistics/transport: carrier contracts, whether freight is priced per shipment, per pallet, or
     via a blanket contract that needs apportioning.
   - Procurement/IT: existing systems already holding a piece of the truth (WMS, TMS, payroll,
     ERP general ledger) - cost tracking is an integration problem as much as a modelling one.

3. Key questions
   - What is the smallest unit we must be able to cost - order, order line, unit, shipment,
     pallet? The grain we pick constrains every allocation rule downstream.
   - Which costs are direct (a shipment's own freight invoice) vs indirect (a warehouse's rent,
     a shift supervisor's salary) that must be *allocated* via a driver (e.g. per unit handled,
     per m² occupied, per labor-hour)?
   - Do we need actuals only, or also committed/accrued cost (a PO issued but not yet invoiced,
     an accrual for this month's rent) so a mid-month report isn't understating true run-rate?
   - Multi-currency: are Warehouses/Stores in different currencies, and if so at what point do we
     convert - at the transaction, or at reporting time with a period rate? Retroactive FX
     restatement is a common source of "why did last month's number change" support tickets.
   - How do allocation rules change over time (a warehouse renegotiates its lease, a new labor
     rate applies from a date), and can we reproduce a report *as it looked* on a past date, not
     just recompute it with today's rules?

4. Challenges and risks
   - Indirect cost allocation is inherently a policy choice (per unit? per m²? per labor-hour?),
     not a fact - getting Finance and Operations to agree on and *document* the driver, and
     revisit it periodically, is the hard part, not the arithmetic.
   - Rule versioning: if the allocation rule changes without being versioned, historical reports
     silently drift and reconciliation against the general ledger breaks.
   - Reconciliation: allocated/estimated cost will diverge from what Finance later posts as
     actuals (see Scenario 3) - without a defined reconciliation cadence, the two "truths" erode
     trust in the tool.
   - Traceability: every allocated cost figure needs to be traceable back to its source
     transaction(s) and the rule version applied, or an auditor's first question ("show me how you
     got this number") has no answer.

5. Proposed approach/capabilities
   - A canonical cost model with explicit dimensions - warehouse, store, product, order/shipment,
     cost centre, period, currency - so any report is a slice of the same underlying facts, not a
     bespoke query per team.
   - Direct costs attached to their originating transaction; indirect costs allocated via named,
     versioned drivers (e.g. "WAREHOUSE_OVERHEAD_V3: per unit handled, effective 2026-01-01"),
     so a report can say which rule version produced it.
   - A distinction, at the data-model level, between allocated/estimated cost and posted actuals
     (from Scenario 3's financial integration), so users are never shown one without knowing which
     it is.

6. Expected outcomes and KPIs
   - Cost per order, cost per unit, cost per shipment, trended by warehouse/store, with drill-down
     to the driver and source transactions behind any number.
   - Allocation variance (allocated vs. posted actual) trending toward zero as rules mature -
     itself a KPI of the model's quality, not just an operational metric.
   - Time-to-traceability: how long it takes to answer "why is this number what it is" for a given
     cost line - should be minutes, via drill-down, not a manual investigation.

7. Assumptions and trade-offs
   - Assumes Finance and Operations can agree on allocation drivers; if they can't, the tool can
     surface the disagreement (report under both driver choices) but can't resolve it.
   - A finer costing grain (per unit vs. per shipment) is more useful but proportionally more
     expensive to instrument and reconcile - I'd start one level coarser than the ideal and refine
     once the coarser model is trusted, rather than over-build the grain up front.
```

## Scenario 2: Cost Optimization Strategies
**Situation**: The company wants to identify and implement cost optimization strategies for its fulfillment operations. The goal is to reduce overall costs without compromising service quality.

**Task**: Discuss potential cost optimization strategies for fulfillment operations and expected outcomes from that. How would you identify, prioritize and implement these strategies?

**Questions you may have and considerations:**
```txt
1. Business objective
   Reduce fulfilment cost per order/unit measurably, without degrading the service-quality metrics
   (on-time delivery, order accuracy, stock availability) that cost reduction could otherwise
   quietly erode.

2. Information and stakeholders needed
   - Operations leadership: current pain points (known-inefficient warehouses, chronic
     under-utilised routes, high damage/return rates).
   - The cost model from Scenario 1 - optimization without a trusted baseline is guesswork.
   - Service-quality owners (customer experience, store ops): the guardrail metrics that must not
     regress, and what "acceptable" looks like for each.
   - Finance: capital availability and payback-period expectations for anything requiring
     investment (automation, equipment).

3. Key questions
   - Where does the cost model (Scenario 1) show the largest, most persistent variance between
     locations doing comparable work - that's usually a better hunting ground than starting from a
     list of generic "best practices."
   - For each candidate strategy: what's the expected saving, the confidence in that estimate, the
     cost/effort to implement, and the risk to service quality if it goes wrong?
   - Which strategies are reversible (a pilot in one warehouse) vs. structural (closing/relocating
     a warehouse) - the latter needs a much higher confidence bar before committing.

4. Challenges and risks
   - Labor and slotting optimizations directly touch service quality (pick accuracy, throughput
     during peaks) - a naive "just reduce headcount" reading of a labor-cost KPI can quietly break
     the guardrail metric it wasn't watching.
   - Consolidating transport/routes for efficiency can extend delivery times - the saving is real
     but so is the service trade-off, and it needs to be measured, not assumed away.
   - Automation and equipment business cases usually require capital and a payback period that
     spans budget cycles (Scenario 4) - a strategy that looks good on unit cost can still be the
     wrong call if the payback horizon doesn't match the business's risk appetite.
   - Attribution: if three optimizations run concurrently, isolating which one drove an observed
     saving needs controlled rollout, not just a before/after comparison across everything at once.

5. Proposed approach/capabilities
   - Prioritize by expected benefit divided by (cost x risk), informed by confidence in the
     estimate - a large, well-understood saving beats a larger but speculative one.
   - Every strategy in scope for labor planning, warehouse slotting, inventory balancing, transport
     consolidation/route optimization, energy, damage/returns reduction, and automation business
     cases gets scored the same way, so operations and finance are comparing like with like.
   - Roll out via controlled pilots (one warehouse, one route) with the service-quality guardrails
     from Scenario 2 wired in as automatic stop conditions, before any network-wide commitment.

6. Expected outcomes and KPIs
   - Realised savings vs. forecast per strategy, tracked at the same grain as the pilot (so
     "network-wide extrapolation" isn't presented as measured fact before it's confirmed at scale).
   - Guardrail dashboard: on-time delivery, order accuracy, stock availability tracked alongside
     the cost KPI for every pilot, not as an afterthought.
   - A running register of "tried, worked/didn't, why" - so the same speculative idea doesn't get
     re-litigated from scratch every planning cycle.

7. Assumptions and trade-offs
   - Assumes the cost model from Scenario 1 is trusted enough to prioritize against; if allocation
     rules are still contested, optimization prioritization inherits that uncertainty.
   - Faster, broader rollout trades off against confidence - I'd bias toward a smaller number of
     well-instrumented pilots over a wide simultaneous rollout, even though it's slower, because an
     unmeasured regression in service quality is expensive to unwind and to trust again afterward.
```

## Scenario 3: Integration with Financial Systems
**Situation**: The Cost Control Tool needs to integrate with existing financial systems to ensure accurate and timely cost data. The integration should support real-time data synchronization and reporting.

**Task**: Discuss the importance of integrating the Cost Control Tool with financial systems. What benefits the company would have from that and how would you ensure seamless integration and data synchronization?

**Questions you may have and considerations:**
```txt
1. Business objective
   Give Finance a cost data feed they can post, audit, and rely on, without duplicate manual entry
   or a standing reconciliation burden between "what operations sees" and "what the books say."

2. Information and stakeholders needed
   - Finance/ERP team: the system of record for posted actuals, its integration surface (API,
     batch file, message bus), and its own close/freeze calendar.
   - Security/compliance: data-sensitivity classification of cost data, audit requirements, access
     control expectations - financial integrations usually carry stricter obligations than
     operational reporting.
   - Operations: what "near-real-time" actually needs to mean for their decisions, versus what
     Finance needs for period-end posting (these are different freshness requirements, see below).

3. Key questions
   - Is the ERP the system of record for posted cost, with the Cost Control Tool an upstream
     estimator/aggregator - or does the tool need to originate journal entries itself? This
     decision shapes the entire integration.
   - What's the actual freshness requirement per consumer? Operational dashboards plausibly want
     near-real-time; financial posting is inherently periodic (a close calendar), and conflating
     the two SLAs leads to either over-building real-time infra Finance doesn't need or
     under-serving operations that do.
   - What happens when a downstream system is unavailable - queue and retry, or drop and
     alert? Silent data loss in a financial pipeline is not an acceptable failure mode.
   - How do we handle a record that needs to be corrected after being sent - reversing entry,
     replay from source, or manual adjustment in the ERP?

4. Challenges and risks
   - "Real-time sync" is often used loosely; without an explicit SLA per consumer, effort gets
     spent building infrastructure nobody asked for while the delivery guarantee that actually
     matters (data isn't silently lost) is underbuilt.
   - Idempotency: any network/consumer hiccup on retry risks double-counting cost events. Every
     event needs a stable, deduplicatable identity.
   - Field-level mapping drift: the tool's cost model and the ERP's chart of accounts will evolve
     independently; without versioned mappings, a schema change on either side silently
     misclassifies costs downstream.
   - Reconciliation and error quarantine: mismatches will happen (timing differences, mapping
     gaps) - they need a visible queue to investigate, not a log line nobody watches.

5. Proposed approach/capabilities
   - Canonical, versioned cost events, published via an API/event stream for real-time consumers
     and batch export for the ERP's own cadence - one source of truth, two delivery shapes.
   - Idempotent delivery (unique event IDs, upsert semantics on the receiving side) so retries are
     always safe.
   - Explicit, versioned field-level mappings from the cost model to the ERP's chart of accounts,
     with a reconciliation job comparing "sent" vs. "posted" and a quarantine queue for anything
     that doesn't reconcile automatically, backed by replay capability from source.
   - Observability (delivery lag, failure rate, quarantine queue depth) and an audit trail of every
     event sent - who/what triggered it, when, and its outcome.

6. Expected outcomes and KPIs
   - Reconciliation match rate between operational cost data and posted financial actuals,
     trending toward 100% with a shrinking, actively-worked quarantine queue.
   - Delivery freshness against the SLA actually agreed with each consumer (not one blanket
     number), and time-to-detect for a failed/delayed delivery.
   - Reduction in manual reconciliation effort for the Finance team - a direct, measurable
     efficiency gain, and usually the easiest benefit to sell as ROI for this integration.

7. Assumptions and trade-offs
   - Assumes the ERP remains system of record for posted actuals; if that's not true, the
     integration's shape (and its audit obligations) change substantially.
   - Near-real-time operational estimates and financially posted actuals are treated as two
     distinct, clearly-labelled things throughout - collapsing them into one number is faster to
     build but erodes trust the first time they disagree (which they will, by timing alone).
```

## Scenario 4: Budgeting and Forecasting
**Situation**: The company needs to develop budgeting and forecasting capabilities for its fulfillment operations. The goal is to predict future costs and allocate resources effectively.

**Task**: Discuss the importance of budgeting and forecasting in fulfillment operations and what would you take into account designing a system to support accurate budgeting and forecasting?

**Questions you may have and considerations:**
```txt
1. Business objective
   Let the business commit to a cost plan with confidence, catch deviations early enough to act
   (not just explain them after the fact), and allocate resources (labor, capital, network
   capacity) against a plan grounded in the same cost model used to track actuals.

2. Information and stakeholders needed
   - FP&A/Finance: budget cycle cadence, approval workflow, existing forecasting methodology (if
     any) to build on rather than replace wholesale.
   - Operations: demand drivers they already track (seasonality, promotional calendars, known
     network changes - a new warehouse opening, a store closing).
   - Procurement/HR: labor rate trends, inflation assumptions, fuel/energy cost outlook - inputs
     that come from outside the fulfilment system entirely.

3. Key questions
   - What's the forecast horizon and cadence - annual budget with monthly re-forecast, rolling
     12-month, both? Different horizons need different levels of granularity and different
     tolerance for assumption drift.
   - Which costs are fixed (warehouse lease), variable (labor scaling with volume, transport
     scaling with shipments), and one-time (a warehouse replacement's capex, see Scenario 5) -
     each needs a different forecasting treatment.
   - What demand drivers actually move fulfilment cost - order volume, seasonality, known network
     changes - and where do those forecasts already live (a demand-planning system) versus needing
     to be built here?
   - How do we version assumptions (this quarter's fuel-price assumption, this year's inflation
     rate) so a forecast is reproducible and its inputs are visible when actuals diverge from it?
   - Do we need scenario planning (base/upside/downside) and, if so, who approves which scenario
     becomes "the" budget?

4. Challenges and risks
   - Forecasts built on stale or unversioned assumptions become silently wrong and nobody notices
     until the variance is large - assumption versioning and an explicit "as-of" date per forecast
     run are what make variance analysis possible at all.
   - Currency and inflation exposure compounds over a long horizon - a rolling forecast with
     periodic re-basing handles this far better than a single annual number fixed in January.
   - Approval workflows that are too heavyweight slow re-forecasting down exactly when the business
     needs it most (a demand shock); too light and budget discipline erodes.
   - Coupling the forecast model too tightly to the actuals cost model (Scenario 1) risks a
     forecast that's really just "last period's actuals plus a growth rate" - useful as a
     baseline, but insufficient once a known network change (Scenario 5) should shift the curve.

5. Proposed approach/capabilities
   - A forecast built from the same cost dimensions as actuals (warehouse, store, cost centre,
     period), so budget-vs-actual variance analysis is a direct comparison, not a translation
     exercise between two different models.
   - Versioned assumption sets (demand growth, labor rate, inflation, fuel/energy, FX) that a
     forecast run references explicitly, so "why did the forecast change" is always answerable.
   - Rolling re-forecast cadence with a lightweight approval workflow for routine updates and a
     heavier one for base-budget commitments, plus explicit scenario support (base/upside/downside)
     for major decisions like a network change.

6. Expected outcomes and KPIs
   - Forecast accuracy (forecast vs. actual, by period and by cost category), trended over time as
     the primary measure of whether the model is improving.
   - Variance explained vs. unexplained - the goal isn't zero variance, it's that most variance
     traces to a named, versioned assumption that moved, not to "unknown."
   - Time from a demand/cost shock being observed to a re-forecast reflecting it - a proxy for how
     useful the rolling model actually is to decision-makers versus a static annual budget.

7. Assumptions and trade-offs
   - Assumes demand-driver forecasts (order volume, seasonality) are available from elsewhere
     (demand planning) rather than needing to be originated in the cost-control tool itself.
   - A finer-grained, more frequently re-forecast model is more accurate but costs more analyst
     time to maintain; I'd start at a coarser grain/cadence aligned to the existing budget cycle
     and increase frequency only where variance analysis shows it's warranted.
```

## Scenario 5: Cost Control in Warehouse Replacement
**Situation**: The company is planning to replace an existing Warehouse with a new one. The new Warehouse will reuse the Business Unit Code of the old Warehouse. The old Warehouse will be archived, but its cost history must be preserved.

**Task**: Discuss the cost control aspects of replacing a Warehouse. Why is it important to preserve cost history and how this relates to keeping the new Warehouse operation within budget?

**Questions you may have and considerations:**
```txt
1. Business objective
   Execute a warehouse replacement (new site assuming an existing business unit's role) without
   losing the cost history needed to (a) prove the replacement's business case and (b) hold the
   new warehouse to a budget informed by the old one's actuals, not a guess.

2. Information and stakeholders needed
   - Finance: the approved capex/opex budget for the replacement, and how it wants replacement
     costs tracked against that budget (vs. forecast, vs. commitments already made).
   - Operations: the old warehouse's actual cost history (the reason cost history preservation
     matters here specifically) and the migration/dual-running plan.
   - HR/facilities/IT: training, lease/construction/equipment, and IT setup costs for the new site
     - each with its own timeline and cost profile distinct from steady-state operating cost.

3. Key questions
   - The business unit code is reused across old and new warehouses by design (per the domain
     model), but a business unit code alone can't be the *cost-history key* - it identifies "the
     role", not "the physical warehouse that incurred a given cost." Without a separate immutable
     identity per warehouse version, a report scoped to "this warehouse's cost" is ambiguous
     about which physical warehouse (old or new) actually incurred it once both have used the same
     code over time.
   - What's the transition model - hard cutover (old archived the moment new goes active, matching
     the domain's archive-on-replace semantics) or a dual-running overlap where both incur cost
     simultaneously (double rent, duplicated labor) for a period? The domain model's instantaneous
     archive/replace doesn't itself capture a multi-week dual-running cost reality - that needs to
     be modelled explicitly if it happens.
   - How is the new warehouse's budget set - as a fresh budget, or as the old warehouse's trailing
     actuals plus known deltas (new lease rate, new automation reducing labor)? The latter is more
     defensible but requires the old warehouse's cost history to be readily queryable.
   - Which replacement costs are one-time (lease deposit, construction, initial equipment, IT
     setup, training) versus ongoing, and how do actuals/commitments get tracked against the
     approved budget as the project proceeds, not just at the end?

4. Challenges and risks
   - If cost history isn't preserved and cleanly attributable to the specific warehouse instance,
     the replacement's ROI can't be demonstrated after the fact ("did the new warehouse actually
     cost less to run") - the business case becomes unverifiable.
   - Reusing a business unit code without an immutable per-version identity risks reports silently
     blending old- and new-warehouse cost once both have existed under that code, especially if
     any reporting query naively filters "by business unit code" without also scoping by which
     warehouse version was active in the period being reported.
   - Dual-running costs (if any) are easy to under-budget because they're incremental to both the
     old warehouse's steady-state cost and the new warehouse's ramp-up cost simultaneously.
   - Contingency: replacement projects (construction, equipment) commonly slip on cost and
     timeline; without a tracked contingency line, overruns surface as a budget miss rather than
     an anticipated, managed risk.

5. Proposed approach/capabilities
   - Model each warehouse as having an immutable warehouse-version identity (independent of the
     business-unit code it currently carries) with valid-from/valid-to dates, a status
     (active/archived), and an explicit replacement link to its predecessor/successor - the
     business unit code stays the stable *business* identifier (what Task 3's persistence layer
     already treats as the reusable, archive-on-replace key), while the version identity is what
     cost history actually attaches to. This mirrors exactly the archive-and-insert pattern already
     implemented for warehouse replacement, extended with an explicit predecessor/successor link.
   - Track one-time replacement costs (lease, construction, equipment, IT, training) as their own
     budget line, separate from the new warehouse's steady-state operating budget, so a slow ramp
     to target throughput doesn't get misread as a steady-state cost overrun.
   - Track approved budget vs. forecast vs. committed (POs issued) vs. actual for the replacement
     project specifically, with a contingency line, so overruns are visible against a plan rather
     than discovered at close.

6. Expected outcomes and KPIs
   - New warehouse's steady-state cost per unit/order versus the old warehouse's trailing actuals -
     the direct evidence for whether the replacement delivered its intended benefit.
   - Replacement project actual cost vs. approved budget, broken out by one-time cost category, and
     contingency utilisation.
   - Post-replacement benefits-realisation review at a fixed point after go-live (e.g. 90/180
     days), comparing the original business case's projected savings to what was actually
     observed - closing the loop on whether the investment paid off, not just whether it was
     delivered on budget.

7. Assumptions and trade-offs
   - Assumes Finance wants replacement cost tracked as a distinct project against its own budget
     rather than folded into the receiving business unit's ongoing opex - this is the more
     rigorous choice but requires more upfront structure (a project/budget line) to set up.
   - Introducing an immutable warehouse-version identity is additional modelling complexity beyond
     what the business-unit code alone provides; the trade-off is worth it specifically because
     cost history and audit traceability are the stated requirement here - a simpler model would
     be adequate if historical cost attribution weren't a hard requirement.
```

## Instructions for Candidates
Before starting the case study, read the [BRIEFING.md](BRIEFING.md) to quickly understand the domain, entities, business rules, and other relevant details.

**Analyze the Scenarios**: Carefully analyze each scenario and consider the tasks provided. To make informed decisions about the project's scope and ensure valuable outcomes, what key information would you seek to gather before defining the boundaries of the work? Your goal is to bridge technical aspects with business value, bringing a high level discussion; no need to deep dive.
