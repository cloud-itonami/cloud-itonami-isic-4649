# Business Model: Wholesale of Other Household Goods

## Classification
- Repository: `cloud-itonami-isic-4649`
- ISIC Rev.5: `4649` — wholesale of other household goods (housewares,
  small appliances, furniture, toys and similar consumer goods not
  classified elsewhere)
- Domain: `downstream/household-goods-wholesale`
- Social impact: consumer safety, child safety, transparency
- Governor: `:consumer-product-safety-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers household-order intake through per-jurisdiction
contract / sanctions / consumer-product-safety regulatory verification,
physical dispatch (releasing real household goods for a counterparty
from the wholesale distribution center), and invoice settlement (the
money side of a wholesale trade, custody / financial transfer) for a
household-goods wholesaler. It does **not**, by itself, hold any
wholesale licence, importer-of-record registration or operating
authority required to run a household-goods-wholesale business in a
given jurisdiction, perform the actual physical warehouse pick/pack/
ship, or judge distribution-network economics (carton/pallet routing and
distribution-network optimization is a follow-up slice, not this R0).
It also does not automatically ingest CPSC's own recall database in
this R0 -- `:recall-status` is operator-maintained (see Implementation
notes). Whoever deploys a live instance supplies the jurisdiction-
specific operating authority, the real warehouse-automation and ERP /
accounts-receivable integrations, and bears that jurisdiction's
liability -- the software supplies the governed, spec-cited, audited
execution scaffold so the operator does not have to build the
compliance layer from scratch.

## Customer
- regional and independent household-goods wholesalers and distributors
- housewares / small-appliance / furniture / toy importers leaving
  closed distribution / ERP SaaS
- big-box and specialty retail buyers who need an auditable, spec-cited
  supply chain
- counterparties, banks and regulators who need an auditable, spec-cited
  trade record

## Offer
- household-order intake and directory management
- per-jurisdiction contract / sanctions regulatory verification with an
  official spec-basis citation
- children's-product safety compliance (CPSIA lead/phthalate testing +
  Children's Product Certificate) gated on the order's own
  `:childrens-product?` fact
- active-recall screening (CPSC-style `:recall-status`) gated at both
  dispatch and invoice settlement
- physical dispatch gated on full evidence, a credit-cleared
  counterparty, contract-terms on file, a valid Children's Product
  Certificate (when applicable), no active unresolved recall, and a
  passed sanctions screen
- invoice settlement with double-invoice prevention
- evidence checklisting (credit-clearance record, contract/PO,
  sanctions-screening record)
- sanctions, credit and recall exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per wholesaler / distribution center
- support retainer with SLA
- ERP and accounts-receivable integration
- CPSC-recall-feed sync integration (follow-up add-on, not in this R0)

## The `:consumer-product-safety-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is
`:consumer-product-safety-governor`. It is the single authority that
stands between "household goods could be dispatched to a counterparty"
and "they are allowed to leave the distribution center," and between
"an invoice could be settled" and "it is allowed to settle." Every rule
it enforces is traceable to the domain (Wholesale of Other Household
Goods, ISIC 4649) and to the three `:social-impact` tags in
`blueprint.edn` (`:consumer-safety`, `:child-safety`, `:transparency`).

This is the rule the companion contract test
(`test/housewaretrade/governor_contract_test.cljk`) encodes end-to-end:
the HousewareTradeAdvisor never dispatches household goods to a
counterparty or settles an invoice the Consumer Product Safety Governor
would reject, `:delivery/dispatch` and `:invoice/settle` NEVER
auto-commit at any phase, `:order/intake` (no direct capital risk) MAY
auto-commit when clean, and every decision (commit OR hold) leaves
exactly one ledger fact.

**Authorizes a physical dispatch (`:delivery/dispatch`) or invoice
settlement (`:invoice/settle`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:safety/verify`, `:delivery/
   dispatch`, or `:invoice/settle` proposal whose jurisdiction has no
   entry in the `housewaretrade.facts` catalog (`:no-spec-basis`). This
   is the direct enforcement of `:transparency`: a jurisdiction whose
   consumer-product-safety / sanctions requirements cannot be traced to
   an OFFICIAL public source is never guessed. The advisor must not
   fabricate a jurisdiction's requirements.
2. **The jurisdiction's required GENERIC evidence is fully on file** --
   for a dispatch or invoice the order's jurisdiction must have been
   verified with a complete counterparty-diligence evidence checklist on
   record: the credit-clearance record, the contract / purchase order,
   and the sanctions-screening (OFAC / equivalent) record
   (`:evidence-incomplete`). Deliberately does NOT include the
   children's-product certificate or recall status -- those are checks
   3-4 below, each its OWN dedicated check.
3. **The counterparty's credit has been cleared** -- the governor reads
   the dedicated `:credit-cleared?` fact on the order and refuses to
   dispatch household goods when credit has NOT been cleared (the
   leasing collateral-coverage discipline, applied to counterparty
   credit) (`:credit-uncleared`). Evaluated at `:delivery/dispatch`.
4. **Contract-terms are on file** -- the governor refuses to dispatch
   when no `:contract-terms` are recorded for the order
   (`:contract-missing`). Household goods never leave the distribution
   center against an undocumented trade. Evaluated at `:delivery/
   dispatch`.
5. **A children's product has a valid Children's Product Certificate on
   file** -- WHEN the order's `:childrens-product?` fact is true, the
   governor requires BOTH `:lead-phthalate-tested?` (CPSIA lead/
   phthalate lab testing) AND `:childrens-product-certificate-on-file?`
   (the CPC itself, CPSA §14) to be true --a genuine NO-OP for a
   general household good (`:childrens-product-certificate-missing`).
   Evaluated at `:delivery/dispatch`. This is a PRE-SHIPMENT check.
6. **The SKU has no active, unresolved recall** -- the governor reads
   the dedicated `:recall-status` fact and refuses to dispatch OR
   invoice a SKU with `:recall-status :open` (a CPSA §15(b)
   substantial-product-hazard recall) (`:active-recall-unresolved`).
   Evaluated UNCONDITIONALLY at BOTH `:delivery/dispatch` and
   `:invoice/settle`, unlike check 5 above -- this is a POST-HOC,
   re-checked concern; see Implementation notes and "Two genuinely
   different regulatory shapes" below.
7. **The counterparty has passed OFAC / equivalent sanctions screening**
   -- the governor reads the dedicated `:sanctions-screened?` fact and
   treats an unresolved sanctions-screening flag as a HARD, un-
   overridable hold (`:counterparty-sanctions-flag-unresolved`). Neither
   goods nor money moves against an unscreened counterparty. Evaluated
   UNCONDITIONALLY at both `:delivery/dispatch` and `:invoice/settle`.
8. **The order has not already been dispatched, and the invoice has not
   already been settled** -- a double dispatch of the same order is
   refused off a dedicated `:dispatched?` fact, and a double invoice off
   a dedicated `:invoiced?` fact (never a `:status` value), the double-
   actuation guard every sibling actor in this fleet enforces
   (`:already-dispatched` / `:already-invoiced`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of
the above fail.** A proposal with no spec-basis, incomplete evidence, an
uncleared counterparty credit, no contract-terms on file, a missing
Children's Product Certificate on a children's product, an active
unresolved recall, an unresolved sanctions-screening flag, or a double
dispatch/invoice is held at the governor node -- a human approver cannot
override these, by construction.

**Always escalates to a human (never auto-commits) for `:delivery/
dispatch` and `:invoice/settle`**, even when every check above is clean.
Dispatching real household goods to a counterparty from the wholesale
distribution center and settling a real invoice (real money moving
between counterparty and wholesaler) are the two real-world actuation
events this actor performs; both are always a human trading
supervisor's call. This is enforced by TWO independent layers that
agree on purpose: the governor's confidence / actuation SOFT gate (a
`:delivery/dispatch` / `:invoice/settle` stake always escalates) and
`housewaretrade.phase`'s phase table, which never puts either op in any
phase's `:auto` set. The `:child-safety` tag is enforced upstream of the
governor at the point checks 5-6 evaluate it directly; the governor's
job is dispatch/invoice authorization integrity, not distribution-
network optimization.

## Two genuinely different regulatory shapes: pre-shipment certificate vs. post-hoc recall

This vertical's own defining regulatory content splits into TWO real,
distinct US CPSC mechanics, and this build deliberately keeps them
architecturally SEPARATE:

- **Children's Product Certificate (checks 5 above)** is a PRE-SHIPMENT
  gate: before a children's product's FIRST sale, a third-party lab
  test must have been run and a CPC must be on file. Once satisfied, it
  does not need to be re-checked at invoice time -- the same fixed,
  onboarding-time shape `:credit-cleared?`/`:contract-terms` already
  have. This build models it as ONE check folding TWO evidentiary arms
  of the SAME determination into ONE rule (the metal-wholesale
  sibling's own single-fact-gated-fold shape) -- explicitly NOT the
  ag-machinery sibling's own TWO-independent-checks shape, because
  `:lead-phthalate-tested?` and `:childrens-product-certificate-on-file?`
  are not two independently-triggered regulatory regimes the way the
  ag-machinery sibling's emissions/ROPS pair are -- they are two arms of
  the SAME CPC-issuance act (a CPC cannot exist without the underlying
  lab test, and a lab test without a filed CPC is not yet a compliant
  certificate).
- **Active recall (check 6 above)** is fundamentally DIFFERENT in kind,
  not merely in degree: it is a POST-HOC, discovered-defect reporting
  concern (CPSA §15(b), 15 U.S.C. §2064(b)) -- a SKU can dispatch
  cleanly TODAY and have a recall open on it NEXT WEEK, discovered from
  field data that did not exist at the original certification. A
  pre-shipment-certificate model (check once, forever satisfied) does
  NOT match this mechanic. This build instead models it as a RE-CHECKED
  FLAG on the order's own `:recall-status` (`:none`/`:open`/`:resolved`
  -- an ENUM, not a boolean, because 'no recall history' and 'a
  resolved recall' are genuinely different, and the former is the
  common case), evaluated at BOTH `:delivery/dispatch` AND
  `:invoice/settle` -- reusing the SAME open-flag-unresolved discipline
  the counterparty-sanctions check already establishes fleet-wide, but
  applied to a NEW axis: the PRODUCT/SKU itself, not the counterparty.
  This vertical therefore has TWO independent 'flag' axes -- WHO you
  are selling to (sanctions) and WHAT you are selling (recall) -- both
  re-checked at every actuation event, for the same underlying reason:
  both are facts that can newly change between a clean dispatch and a
  later invoice settlement.

**Why not fold both into one 'certification-missing' rule?** Considered
and rejected: folding would erase the audit ledger's ability to
distinguish "we shipped a lead-tainted toy" from "we shipped a
already-recalled appliance" -- two very different regulator/insurer/
plaintiff's-counsel-facing events -- and would force the PRE-shipment
check's dispatch-only evaluation span onto the recall check, silently
letting a mid-lifecycle recall slip through to invoice settlement
uncaught.

**Why not model the recall check as a pre-shipment certificate too**
(e.g. `:recall-clearance-certificate-on-file?`, checked once like the
CPC)? Considered and rejected: this would be dishonest to the actual
regulatory mechanic. CPSA §15(b) is not a "prove clean before selling"
duty -- it is a "keep monitoring and report/stop-selling the moment you
learn of a defect" duty. A one-time pre-shipment check would let a SKU
that dispatched cleanly in January keep dispatching cleanly in June even
after an intervening recall, which is exactly the harm the reporting
duty exists to prevent. See
`docs/adr/0001-architecture.md` Decision 5 for the full reasoning and
the alternatives considered.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business,
and what each one is actually load-bearing for here (not a generic capability
list):

| Technology | What it is FOR in Wholesale of Other Household Goods |
|---|---|
| `:robotics` | The AS/RS (automated storage and retrieval system) / robotic case-picking-and-palletizing shuttle that stages a SKU's carton or pallet at the wholesale distribution-center dock -- housewares, small appliances and toys are carton/pallet-scale goods well-suited to this apparatus in real-world distribution-center practice. The governor never dispatches hardware itself: a dispatch-clearing action must have cleared the same sign-off a human trading supervisor would need (see Robotics Premise). |
| `:identity` | Trader, trading-supervisor, distribution-center-operator and counterparty identity plus role-based access, so the governor's sign-off is tied to *who* authorized a dispatch or invoice, not just *that* someone did. |
| `:forms` | Structured intake for household-order booking, per-jurisdiction evidence capture (credit-clearance record, contract/PO, sanctions-screening record), children's-product lab-test/CPC recording, recall-status updates, and sanctions / credit exception submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:consumer-product-safety-governor` Decision Rule itself (spec-basis, evidence completeness, credit-clearance, contract-on-file, Children's Product Certificate, active-recall status, sanctions-screening, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> dispatch -> settle -> audit loop end-to-end (see `docs/operator-guide.md`) across household-order intake, safety verification, physical dispatch, and invoice settlement, including the sanctions / credit / recall escalation gate. |
| `:audit-ledger` | The immutable record of every verification, dispatch, invoice, sanctions flag, recall flag, and hold -- this is what "an auditable, spec-cited trade record for every dispatch and invoice" (Trust Controls, below) actually means in practice, and the evidence an operator needs if a dispatch or an invoice is later disputed, or a recall is later litigated. |
| `:optimization` | Carton/pallet routing and distribution-network optimization -- selects the profitable fulfillment strategy for a distribution center. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:housewaretrade` capability library in this stack
(unlike a sibling with its own bespoke domain library): the household-
goods-trading checks (credit-clearance, contract-on-file, children's-
product certification, active-recall status, sanctions-screening) are
direct entity boolean/enum reads in `housewaretrade.governor`, on top
of the generic robotics/identity/forms/dmn/bpmn/audit-ledger stack (see
Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be verified,
  dispatched, or invoiced against
- a dispatch never starts with incomplete counterparty-diligence
  evidence
- a dispatch never starts with an uncleared counterparty credit, no
  contract-terms on file, a missing Children's Product Certificate on a
  children's product, or an active unresolved recall
- an invoice never settles against an active unresolved recall or an
  unresolved sanctions-screening flag
- sanctions / credit / recall flags cannot be silently suppressed
- the same order can never be dispatched or invoiced twice
- a dispatch or invoice never auto-commits; both always need a human
  trading supervisor
- every dispatch and invoice (commit OR hold) leaves exactly one
  immutable ledger fact
- counterparty, credit, product-testing, certificate and recall data
  stays outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by
`housewaretrade.governor` as seven HARD checks (a human approver cannot
override them) plus one SOFT gate, plus two double-actuation guards:

- `spec-basis-violations` -- the spec-basis check above, evaluated on
  every `:safety/verify`, `:delivery/dispatch`, and `:invoice/settle`.
- `evidence-incomplete-violations` -- the evidence-completeness check
  above, for `:delivery/dispatch` / `:invoice/settle`.
- `credit-uncleared-violations` -- the counterparty-credit check above
  (the leasing collateral-coverage discipline applied to counterparty
  credit); evaluated on every `:delivery/dispatch`.
- `contract-missing-violations` -- the contract-on-file check above;
  evaluated on every `:delivery/dispatch`.
- `childrens-product-certificate-missing-violations` -- the PRE-
  SHIPMENT check above, type-gated on `:childrens-product?`, folding
  `:lead-phthalate-tested?` AND `:childrens-product-certificate-on-file?`
  into one rule; evaluated on every `:delivery/dispatch`.
- `active-recall-unresolved-violations` -- the POST-HOC, re-checked
  check above, reading `:recall-status`; evaluated UNCONDITIONALLY on
  BOTH `:delivery/dispatch` and `:invoice/settle`.
- `counterparty-sanctions-flag-unresolved-violations` -- the sanctions-
  screening check above (the same open-flag-unresolved discipline the
  fuel-wholesale sibling's own check establishes); evaluated
  unconditionally on both `:delivery/dispatch` and `:invoice/settle`.
- `already-dispatched-violations` / `already-invoiced-violations` -- the
  double-actuation guards above, off dedicated `:dispatched?` /
  `:invoiced?` booleans (never a `:status` value), the same discipline
  every sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:delivery/dispatch` / `:invoice/settle` stake, escalates to a human;
  and `housewaretrade.phase` independently never auto-commits either op
  at any phase.

`:recall-status` is operator-maintained in this R0: a compliance
officer records a recall's discovery or resolution via the SAME
`:order/intake` upsert path every other order-directory correction
uses (`test/housewaretrade/governor_contract_test.cljk`'s
`recall-resolution-allows-dispatch-on-the-same-sku` proves this
end-to-end). A real CPSC recall-feed sync integration (automatically
setting `:recall-status :open` from CPSC's own published recall data)
is a follow-up slice, not in this R0 -- see README `Business-process
coverage`.

Unlike the crude-extraction sibling's governor (which calls pure
physical range-check functions in its registry), this governor needs no
range-check functions at all: its domain checks read the
`household-order` record's own dedicated booleans/enum directly.
`:delivery/dispatch` and `:invoice/settle` are the two real-world
actuation events (`#{:delivery/dispatch :invoice/settle}`), applied
SEQUENTIALLY to the SAME household-order (dispatch first, invoice
settlement later), the same sequential dual-actuation shape the fuel-
wholesale / ag-machinery-wholesale siblings use. Neither ever
auto-commits at any phase. Carton/pallet routing and distribution-
network optimization (the `:optimization` line above) is a follow-up
slice, not in this R0 build -- see README `Business-process coverage`.

## Capability layer

Unlike a sibling with its own bespoke capability library, this vertical
is SELF-CONTAINED: there is no `kotoba-lang/housewaretrade` to delegate
household-goods-trading validation to. The credit-clearance /
contract-on-file / children's-product-certification / active-recall /
sanctions-screening checks live as direct entity boolean/enum reads in
`housewaretrade.governor` (off dedicated `:credit-cleared?` /
`:contract-terms` / `:childrens-product-certificate-on-file?` /
`:recall-status` / `:sanctions-screened?` facts on the
`household-order` record) -- this vertical's governor needs no pure
range-check functions at all, because its domain checks ARE direct
boolean/enum reads.

## Jurisdiction coverage (honest)

`housewaretrade.facts/catalog` currently seeds 2 jurisdictions with an
official spec-basis, each a REAL regime: the United States (U.S.
Consumer Product Safety Commission, CPSC -- Consumer Product Safety
Improvement Act of 2008, 15 U.S.C. §1278a lead-content limit, 16 C.F.R.
Part 1307 phthalate restrictions, 16 C.F.R. Part 1303 lead-paint ban,
CPSA §14 / 16 C.F.R. Parts 1107/1110 Children's Product Certificate,
CPSA §15(b) / 15 U.S.C. §2064(b) recall reporting, 16 C.F.R. Parts
1632/1633 mattress flammability, Flammable Fabrics Act / 16 C.F.R. Parts
1630/1631 carpet-and-rug flammability, OFAC sanctions programs) and
Japan (METI / Consumer Affairs Agency -- 消費生活用製品安全法 Consumer
Product Safety Act, PSC-mark conformity system, serious-product-
accident reporting, OFAC-equivalent sanctions programs). This is a
starting catalog to prove the governor contract end-to-end, not a claim
of global coverage (2 of ~194 jurisdictions worldwide). Adding a
jurisdiction is additive: one map entry in
`housewaretrade.facts/catalog`, citing a real official source -- never
fabricate a jurisdiction's requirements to make coverage look bigger.

**This build's own confidence caveats, flagged for independent
verification before operational reliance (the same "honest R0 coverage"
discipline every sibling's own facts namespace follows):**

- **16 C.F.R. Part 1633's exact authorizing statute.** This build cites
  16 C.F.R. Part 1633 (mattress open-flame flammability, 2006) as a
  CPSC standard distinct from the Flammable Fabrics Act's own carpet/
  rug standards (16 C.F.R. Parts 1630/1631). This build's confidence
  that Part 1633 was promulgated under direct Consumer Product Safety
  Act rulemaking authority (following the Consumer Product Safety
  Amendments of 1976, which extended CPSC's mattress-flammability
  rulemaking beyond the original FFA delegation) rather than directly
  under the FFA itself is MODERATE, not high.
- **No current federal upholstered-furniture flammability standard
  assumed.** This build deliberately does NOT cite a national mandatory
  flammability standard for upholstered furniture generally (as opposed
  to mattresses specifically) -- to this build's knowledge no such
  federal CPSC rule is currently in force; California's Bureau of
  Household Goods and Services Technical Bulletin 117-2013 is a STATE
  standard and is NOT treated as a national spec-basis here. If a
  federal rule has since been adopted, this catalog entry should be
  updated to cite it.
- **Japan's 10-day serious-product-accident reporting deadline.** This
  build's understanding of 重大製品事故報告制度's exact reporting window
  (cited as approximately 10 days from a business operator becoming
  aware) is of MODERATE confidence and should be independently
  re-verified against the current 消費生活用製品安全法 text before
  operational reliance.
- **EU General Product Safety Regulation (EU) 2023/988 NOT seeded.**
  This build has reasonable confidence the GPSR (which replaced the
  2001/95/EC General Product Safety Directive, applicable from 13
  December 2024) and the Toy Safety Directive 2009/48/EC (EN 71 test
  standards, CE marking, for the toy subset) exist and are named
  correctly, but is NOT confident enough in the precise GPSR/sector-
  directive scope boundary for household-goods categories to seed a
  catalog entry without risking an overclaimed spec-basis. A future PR
  should research this boundary properly before adding an EU entry.

## Maturity

`:implemented` -- `HousewareTradeAdvisor` + `Consumer Product Safety
Governor` run as real, tested code (`kbb -M:dev:test`: see the
repository's own test-run output for current counts; lint clean),
following the SAME governed-actor architecture as the other prior
actors across this fleet, with its own distinct, independently-named
governor and its own direct-entity-boolean/enum household-goods-trading
checks. See `docs/adr/0001-architecture.md` for the history and design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. In this domain
an AS/RS (automated storage and retrieval system) / robotic case-
picking-and-palletizing shuttle stages a SKU's carton or pallet at the
wholesale distribution-center dock, under the actor, gated by the
independent **Consumer Product Safety Governor**. The governor never
dispatches hardware itself: a dispatch-clearing action must have
cleared the same sign-off a human trading supervisor would need. A
robot may stage a carton, but only after the governor (every HARD check
clean) and a human supervisor both agree it is safe to -- the same
operating-state-machine-gated-by-governor premise every cloud-itonami
vertical restates (ADR-2607011000): the blueprint declares `:robotics
true`, the README names the robot that performs the physical act, and
the Consumer Product Safety Governor is the independent gate that
robot's command must pass.

**Why `:robotics true` here, unlike the ag-machinery-wholesale
sibling's own `:robotics false`?** Considered carefully, not defaulted:
the ag-machinery sibling's `:delivery/dispatch` op gates a
self-propelled machine driven off the lot by a human operator, or a
towed implement hitched by a human-driven tow vehicle -- no comparable
fixed, robot-actuatable apparatus exists for THAT entity in general
commercial practice. This vertical's own governed entity is different:
housewares, small appliances and toys (the majority of this ISIC
category's catalog) are genuinely carton/pallet-scale SKUs, and AS/RS
and robotic case-picking/palletizing IS a real, common, fixed apparatus
at household-goods and consumer-electronics distribution centers today
(the SAME apparatus class the telecom/electronics-wholesale sibling's
own `:robotics true` blueprint cites for carton-scale electronics).
Furniture is bulkier than housewares/appliances/toys but is still
commonly palletized/crated and moved via pallet-shuttle/AGV automation
in modern distribution centers, unlike a self-propelled tractor that
drives itself off a lot under a human operator's direct control. See
`docs/adr/0001-architecture.md` Decision 10 for the full reasoning,
including the alternative considered and rejected.
