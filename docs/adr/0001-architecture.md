# ADR-0001: HousewareTradeAdvisor ⊣ Consumer Product Safety Governor architecture

## Status

Accepted. `cloud-itonami-isic-4649` published directly at `:implemented`
in the `kotoba-lang/industry` registry (no prior `:blueprint`-tier-only
scaffold stage for this repo).

## Context

`cloud-itonami-isic-4649` publishes an OSS business blueprint for
wholesale of other household goods (household-order intake,
per-jurisdiction contract / sanctions regulatory verification,
consumer-product-safety compliance verification, physical dispatch, and
invoice settlement). Like every prior actor in this fleet, the
blueprint alone is not an implementation: this ADR records the
governed-actor architecture that establishes it as real, tested code,
following the same langgraph StateGraph + independent Governor + Phase
0->3 rollout pattern established by `cloud-itonami-isic-6511` (life
insurance) and applied across many prior siblings, most closely the
fuel-wholesale (`cloud-itonami-isic-4671`), ag-machinery-wholesale
(`cloud-itonami-isic-4653`) and telecom/electronics-wholesale
(`cloud-itonami-isic-4652`) siblings.

Like the fuel-wholesale and ag-machinery-wholesale siblings, this
vertical has NO bespoke domain capability library in `kotoba-lang` to
wrap (no `kotoba-lang/housewaretrade`-style repo exists). This build
therefore uses self-contained domain logic -- the same pattern the
majority of this fleet's actors use. The household-goods-trading checks
(credit-clearance, contract-on-file, children's-product certification,
active-recall status, sanctions-screening) are direct entity boolean/
enum reads in `housewaretrade.governor`, off dedicated
`:credit-cleared?` / `:contract-terms` /
`:childrens-product-certificate-on-file?` / `:recall-status` /
`:sanctions-screened?` facts on the `household-order` record -- NO pure
range-check functions are needed.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:consumer-product-safety-governor`, is a fresh, independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:consumer-product-safety-governor` names this build's own independent
authority. This build follows the SAME governed-actor architecture as
every prior actor, but with its own distinct governor identity.

### Decision 2: self-contained domain logic, direct entity booleans/enum (no `kotoba-lang/housewaretrade` to wrap, and no range-check functions to host)

Unlike a sibling that delegates to a real, pre-existing bespoke
capability library, and unlike the crude-extraction sibling (which
hosts pure physical range-check functions in its registry because its
governor re-verifies measured physical values), this household-goods-
wholesale vertical needs NEITHER: there is no pre-existing household-
goods-trading capability library to delegate to, AND the governor's
domain checks (credit-clearance, contract-on-file, children's-product
certification, active-recall status, sanctions-screening) are direct
entity boolean/enum reads off the `household-order` record's own
dedicated facts -- not measured-value-vs-limit range comparisons. So
`housewaretrade.registry` is RECORD CONSTRUCTION ONLY (no range-check
functions), and `housewaretrade.governor` reads the order's booleans/
enum directly.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `household-order` entity

Like the fuel-wholesale and ag-machinery-wholesale siblings' own order
entities, this vertical's `dispatch` and `settle` actuation events apply
SEQUENTIALLY to the SAME `household-order` -- physical dispatch happens
first (goods leave the wholesale distribution center), invoice
settlement happens later (the money side of the trade, custody /
financial transfer), on the same order record. This matches the
sequential shape every principal-trading sibling in this fleet uses,
unlike a `:kind`-distinguished alternative-action shape. `high-stakes`
is `#{:delivery/dispatch :invoice/settle}`; neither ever auto-commits at
any phase.

### Decision 4: Children's Product Certificate is ONE check folding TWO evidentiary arms of the SAME determination -- the metal-wholesale shape, NOT the ag-machinery shape

`childrens-product-certificate-missing-violations` is type-gated on a
SINGLE fact, `:childrens-product?`, and internally requires BOTH
`:lead-phthalate-tested?` (third-party lab testing against the CPSIA
100 ppm total-lead-content limit, 15 U.S.C. §1278a, and the 16 C.F.R.
Part 1307 phthalate restrictions) AND `:childrens-product-certificate-
on-file?` (the Children's Product Certificate itself, CPSA §14, 15
U.S.C. §2063, 16 C.F.R. Parts 1107/1110).

This is deliberately modeled as the metal-wholesale sibling's own
single-fact-gated-fold shape (ONE check, gated on ONE boolean, folding
two sub-facts of the SAME real-world determination into ONE rule) --
NOT the ag-machinery sibling's own shape (TWO SEPARATE checks, each
gated on its OWN independent boolean). The reason: `:lead-phthalate-
tested?` and `:childrens-product-certificate-on-file?` are TWO
EVIDENTIARY ARMS OF THE SAME ACT -- a CPC cannot legally exist without
the underlying lab test having been run, and a lab test without a filed
CPC is not yet a compliant certificate either. Missing EITHER one is
EQUALLY 'no valid CPC on file'. Contrast the ag-machinery sibling's own
emissions-certificate/ROPS-certificate pair, which are governed by TWO
GENUINELY DIFFERENT regulatory regimes (air quality vs. operator
safety) triggered by TWO INDEPENDENT machine properties that vary
separately (a machine can be engine-powered without being ride-on, and
vice versa) -- THIS vertical has no such independent-triggering-property
structure for its own certification concern: `:childrens-product?` is
the ONLY gating fact, and the two evidentiary sub-facts are not
independently triggered by anything else.

`housewaretrade.store/demo-data`'s `ho-1` (a general household good,
NEITHER evidentiary sub-fact on file) proves the type-gate: dispatches
cleanly, because a general household good has no CPC to certify against
in the first place. `ho-6` (a children's product, BOTH sub-facts true)
proves the check is satisfiable. `ho-7` (a children's product, lab-
tested but NO CPC actually filed) proves the fold requires BOTH arms,
not merely that testing occurred -- a realistic gap, since lab testing
often completes well before the CPC paperwork itself is finalized and
filed.

### Decision 5: active recall is a POST-HOC, RE-CHECKED flag, NOT a pre-shipment certificate -- this vertical's own genuine structural novelty

This is the central design decision of this build, addressed directly
because it does not force-fit any prior sibling's own check shape.

**The regulatory mechanic.** Consumer Product Safety Act §15(b), 15
U.S.C. §2064(b), requires a manufacturer, importer, distributor or
retailer to report to CPSC within 24 HOURS of obtaining information
reasonably supporting the conclusion a product contains a defect that
could create a substantial risk of injury, does not comply with an
applicable safety rule, or creates an unreasonable risk of serious
injury or death. This is fundamentally NOT a "prove clean before first
sale" duty like the Children's Product Certificate above -- it is a
"keep monitoring, and report/stop-selling the moment you learn of a
defect" duty. A SKU can dispatch cleanly today and have a recall opened
on it next week, discovered from field data (consumer complaints,
injury reports, incident databases) that did not exist at the time of
the original sale or certification.

**Why a pre-shipment-certificate model was considered and REJECTED.**
The natural first instinct, mirroring Decision 4's shape, would be a
`:recall-clearance-certificate-on-file?` boolean checked once, like the
CPC. This was explicitly rejected: a one-time pre-shipment check would
let a SKU that dispatched cleanly in the past keep dispatching cleanly
FOREVER even after an intervening recall is discovered -- exactly the
harm the CPSA §15(b) reporting duty exists to prevent. The real-world
mechanic this actor must gate is "does this SKU have an open,
unresolved CPSC recall ON FILE RIGHT NOW", re-evaluated at the moment of
EVERY actuation, not "was a certificate obtained once, in the past".

**The chosen model.** `active-recall-unresolved-violations` reads a
NEW kind of domain fact, `:recall-status`, an ENUM
(`:none`/`:open`/`:resolved`) rather than a boolean -- because a SKU's
recall history is not binary: 'no recall history at all' (`:none`, the
common case) is genuinely different information from 'a recall that has
since been resolved' (`:resolved`), even though both dispatch/invoice
cleanly. Only `:open` HARD-blocks. This reuses the SAME open-flag-
unresolved DISCIPLINE `counterparty-sanctions-flag-unresolved-
violations` already establishes fleet-wide (an open concern cannot be
silently suppressed to force an actuation through), but applies it to a
NEW axis this fleet has not yet modeled explicitly: the PRODUCT/SKU
itself, rather than the counterparty. This vertical therefore has TWO
independent 'flag' axes -- WHO you are selling to (counterparty
sanctions) and WHAT you are selling (product recall) -- both
UNCONDITIONALLY re-checked at BOTH `:delivery/dispatch` and
`:invoice/settle`, for the SAME underlying reason: both are facts that
can newly change BETWEEN a clean dispatch and a later invoice
settlement, unlike `:credit-cleared?`/`:contract-terms?`/the Children's
Product Certificate (Decision 4), which are onboarding-time facts fixed
at the point of dispatch.

**The end-to-end lifecycle proof.** `housewaretrade.store/demo-data`'s
`ho-8` (a general household good -- a small appliance, NOT a children's
product -- with `:recall-status :open`) HARD-holds at
`:delivery/dispatch`, proving the recall check is genuinely independent
of Decision 4's children's-product gate.
`test/housewaretrade/governor_contract_test.cljk`'s
`recall-resolution-allows-dispatch-on-the-same-sku` then proves the
SAME order/SKU, once patched to `:recall-status :resolved` via a plain
`:order/intake` upsert (the SAME low-stakes normalize-and-merge op
every sibling's advisor already uses for order-directory corrections --
no new op needed for a compliance officer to record a recall's
resolution), dispatches CLEANLY afterward. A companion test,
`active-recall-unresolved-also-blocks-a-later-invoice-settle`, proves
the flag also blocks a LATER `:invoice/settle` on an order that
dispatched cleanly BEFORE a recall was discovered -- confirming the
check's dispatch-AND-invoice span is load-bearing, not incidental.

**Explicit disambiguation from `cloud-itonami-isic-6492`'s real
status-lifecycle bug (ADR-2607071320).** That bug arose from using a
single `:status` value to ALSO serve as a double-actuation guard.
`:recall-status` here is NOT such a guard -- the double-actuation
guards for THIS entity remain the dedicated `:dispatched?`/`:invoiced?`
booleans (Decision 6), exactly like every sibling.
`already-dispatched-violations`/`already-invoiced-violations` never
read `:recall-status`. `:recall-status` is instead a THIRD kind of
domain fact, parallel in KIND to `:jurisdiction` or
`:sanctions-screened?` (external regulatory/counterparty ground truth
about the order), not a workflow-lifecycle marker.

### Decision 6: dedicated double-actuation-guard booleans

`:dispatched?` / `:invoiced?` are dedicated booleans on the
`household-order` record, never a single `:status` value -- the same
discipline every prior governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320). See Decision 5 for why `:recall-status` is
deliberately NOT such a guard despite superficially resembling one.

### Decision 7: Store protocol, MemStore + DatomicStore parity

`housewaretrade.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore`
(`langchain.db`-backed), proven to satisfy the same contract in
`test/housewaretrade/store_contract_test.cljk`. The ledger stays
append-only on every backend: which household-order was verified for a
jurisdiction with no official spec-basis, which counterparty had
credit-uncleared / no contract / a missing Children's Product
Certificate / an active unresolved recall / an unresolved sanctions-
screening flag, which order was dispatched, which invoice was settled,
on what jurisdictional basis, approved by whom -- always a query over an
immutable log.

### Decision 8: Phase 0->3 with `:delivery/dispatch`/`:invoice/settle` NEVER auto

`housewaretrade.phase`'s phase table puts `:order/intake` (no direct
capital risk) in phase 3's `:auto` set as its only member;
`:delivery/dispatch` and `:invoice/settle` are deliberately ABSENT from
every phase's `:auto` set, including phase 3 -- a permanent structural
fact. `housewaretrade.governor`'s high-stakes gate enforces the same
invariant independently: two layers agree that actuation is always a
human trading supervisor's call.

### Decision 9: mock + LLM advisor pair

`housewaretrade.housewaretradeadvisor` provides a deterministic
`mock-advisor` (default, runs offline) and an `llm-advisor` backed by a
`langchain.model/ChatModel`. The LLM advisor's EDN proposal is parsed
defensively: any parse/shape failure yields a safe low-confidence noop
so the governor escalates/holds -- an LLM hiccup can never auto-dispatch
household goods or auto-settle an invoice.

### Decision 10: `:robotics true` -- carton/pallet-scale AS/RS automation, a reasoned departure from the ag-machinery sibling's `:robotics false`

Considered carefully rather than defaulted either way. The ag-machinery
sibling set `:robotics false` because its own `:delivery/dispatch` op
gates a self-propelled machine driven off the lot by a human operator,
or a towed implement hitched by a human-driven tow vehicle -- no
comparable fixed, robot-actuatable apparatus exists for THAT entity in
general commercial practice today.

This vertical's own governed entity is materially different: ISIC
4649's catalog is dominated by housewares, small appliances and toys --
genuinely carton/pallet-scale SKUs. AS/RS (automated storage and
retrieval systems) and robotic case-picking/palletizing shuttles are a
real, common, FIXED apparatus already deployed at household-goods and
consumer-electronics distribution centers today (the SAME apparatus
class the telecom/electronics-wholesale sibling's own `:robotics true`
blueprint already cites for carton-scale electronics goods). Furniture
(also within ISIC 4649's scope) is bulkier than housewares/appliances/
toys, but remains palletizable/crateable and is commonly moved via
pallet-shuttle/AGV automation in modern distribution centers -- unlike a
self-propelled agricultural tractor that drives itself off a lot under
direct human operator control, which has no comparable fixed apparatus
at all. `blueprint.edn` therefore sets `:itonami.blueprint/robotics
true`, and `:required-technologies` includes `:robotics`.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/housewaretrade` capability
  library.** Considered and explicitly ruled out: no such library
  exists. Forcing a false capability-library integration would be
  dishonest; this build correctly uses self-contained domain logic
  instead.
- **Folding `childrens-product-certificate-missing` into TWO
  independent checks** (mirroring the ag-machinery sibling's emissions/
  ROPS split). Considered and ruled out (see Decision 4): the lab-test
  and CPC-on-file facts are two evidentiary arms of the SAME
  determination, not two independently-triggered regulatory regimes --
  splitting them would manufacture a false independence the underlying
  law does not have.
- **Modeling `active-recall-unresolved` as a pre-shipment certificate**
  (a one-time `:recall-clearance-certificate-on-file?` boolean, checked
  once like the CPC). Considered and REJECTED as the central design
  decision of this build (see Decision 5): this would misrepresent CPSA
  §15(b)'s actual "keep monitoring" mechanic as a "prove clean once"
  mechanic, silently allowing a SKU that dispatched cleanly in the past
  to keep dispatching after an intervening recall.
- **Folding `active-recall-unresolved` into
  `childrens-product-certificate-missing`** (one combined 'product-
  safety-missing' rule). Considered and ruled out: this would erase the
  audit ledger's ability to distinguish two very different regulator/
  insurer-facing events (a certification gap vs. a discovered defect),
  and would force the certification check's dispatch-only evaluation
  span onto the recall check, letting a mid-lifecycle recall slip
  through to invoice settlement uncaught.
- **A `:kind`-distinguished entity** (matching an alternative-action
  shape rather than sequential). Rejected: dispatch and invoice
  settlement happen SEQUENTIALLY on the SAME household-order in this
  domain, not as alternative actions -- the fuel-wholesale / ag-
  machinery-wholesale cluster's sequential shape is the honest match
  here.
- **`:robotics false`, mirroring the ag-machinery sibling.** Considered
  and rejected (see Decision 10): this vertical's own catalog (house-
  wares, small appliances, toys) is dominated by carton/pallet-scale
  goods with a real, common, fixed AS/RS/robotic-case-picking apparatus
  in commercial distribution-center practice, materially unlike the
  ag-machinery sibling's own self-propelled/towed large-equipment
  entity.
- **Building carton/pallet routing / distribution-network optimization
  in this R0.** Rejected in favor of a scoped R0 slice (the
  `:optimization` capability is correctly marked required, the
  integration is a follow-up), consistent with this fleet's 'extending
  coverage is additive' convention.
- **Automatically ingesting CPSC's own recall database in this R0.**
  Rejected in favor of operator-maintained `:recall-status` updates via
  the existing `:order/intake` patch path; a real CPSC recall-feed sync
  integration is a follow-up slice (see README `Business-process
  coverage`).

## Consequences

- Fresh independent actor in this fleet, following the SAME
  governed-actor architecture as every prior sibling.
- Establishes a NEW check shape for this fleet's own domain-defining
  content: two genuinely different temporal shapes (a pre-shipment
  certificate fold vs. a post-hoc re-checked flag) coexisting within
  ONE vertical, proven distinct from both the ag-machinery sibling's
  own two-independent-checks shape and the metal-wholesale sibling's
  own single-check fold (which this build's Children's Product
  Certificate check most closely resembles).
- `MemStore` || `DatomicStore` parity is proven by
  `test/housewaretrade/store_contract_test.cljk`.
- 41 tests / 217 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean dispatch + invoice lifecycle,
  the children's-product type-gating proof, the full active-recall
  open -> resolved lifecycle proof on the SAME order/SKU, plus six other
  HARD-hold scenarios, end-to-end.
- `blueprint.edn` sets `:robotics true` and includes `:robotics` in
  `:required-technologies`, a reasoned departure from the ag-machinery
  sibling's own `:robotics false` grounded in this vertical's own
  carton/pallet-scale catalog.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4671/docs/adr/0001-architecture.md` (fuel-
  wholesale sibling; origin of the sequential dual-actuation shape and
  the self-contained-domain-logic pattern this build follows)
- `cloud-itonami-isic-4653/docs/adr/0001-architecture.md` (ag-machinery-
  wholesale sibling; contrast: two checks split on two independent
  booleans, and `:robotics false` reasoning this build's own Decision 10
  distinguishes from)
- `cloud-itonami-isic-4652` (telecom/electronics-wholesale sibling;
  `:robotics true` precedent for carton-scale goods this build's own
  Decision 10 draws on)
- Consumer Product Safety Improvement Act of 2008 (CPSIA, Pub. L.
  110-314); 15 U.S.C. §1278a (children's product lead-content limit);
  16 C.F.R. Part 1307 (phthalates); 16 C.F.R. Part 1303 (lead paint) --
  US CPSC
- Consumer Product Safety Act §14, 15 U.S.C. §2063; 16 C.F.R. Parts
  1107/1110 (Children's Product Certificate / General Certificate of
  Conformity) -- US CPSC
- Consumer Product Safety Act §15(b), 15 U.S.C. §2064(b) (24-hour
  substantial-product-hazard report) -- US CPSC
- 16 C.F.R. Part 1632 / Part 1633 (mattress flammability); Flammable
  Fabrics Act, 15 U.S.C. §1191 et seq., 16 C.F.R. Parts 1630/1631
  (carpet/rug flammability) -- US CPSC
- 消費生活用製品安全法 (Consumer Product Safety Act, Act No. 31 of 1973)
  -- Japan, METI / Consumer Affairs Agency
