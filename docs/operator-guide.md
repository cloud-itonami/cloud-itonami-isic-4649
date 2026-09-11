# Operator Guide

## First Deployment
1. Register wholesalers, distribution centers, household-orders, and
   trading supervisors.
2. Import household-order, counterparty, credit, sanctions and trade
   history.
3. Seed the per-jurisdiction spec-basis catalog (`housewaretrade.facts`)
   for the jurisdictions you actually trade in, citing real official
   sources only.
4. Run read-only spec-basis validation per jurisdiction.
5. Configure sanctions / credit / recall escalation and
   accounts-receivable accounts.
6. Publish a dry-run dispatch/invoice and audit export.

## Minimum Trading Controls
- spec-basis validation before any verification, dispatch, or invoice
- full counterparty-diligence evidence (credit-clearance record,
  contract/PO, sanctions-screening record) before any dispatch
- credit-clearance, contract-on-file, Children's Product Certificate
  (for children's products) and sanctions-screening checks before any
  dispatch; active-recall and sanctions-screening checks before any
  invoice
- sanctions / credit / recall escalation gate
- audit export for every dispatch, invoice, and hold
- backup manual dispatch and invoicing process

## A Day in the Life: Intake → Verify → Dispatch → Settle → Audit

Wholesale of Other Household Goods (ISIC 4649,
`cloud-itonami-isic-4649`) runs on the same intake / advise / govern /
decide / commit-or-hold loop as every itonami blueprint, but here the
loop is concrete: a regional household-goods wholesaler needs to bring
a household-order (say, a pallet of toys destined for a nursery
retailer in the United States) from intake through safety verification
to a physical dispatch and an invoice settlement. Walking through one
order, end to end:

1. **Intake.** The wholesaler books the household-order through
   `:forms`: order-id, product-category, SKU, counterparty, price,
   contract-terms, jurisdiction, whether it is a children's product, and
   the order's own diligence record (credit-cleared?, sanctions-
   screened?). This creates a household-order record at `:order/intake`
   status. The HousewareTradeAdvisor only normalizes the patch; it does
   not invent the order-id, counterparty, jurisdiction, product-
   category, or any commercial/diligence value.
2. **Verify.** The HousewareTradeAdvisor drafts a per-jurisdiction
   contract / sanctions evidence checklist (`:safety/verify`) from
   `housewaretrade.facts`, citing the jurisdiction's official spec-basis
   (owner authority, legal basis, provenance) and listing the required
   evidence (credit-clearance record, contract/PO, sanctions-screening
   record). The `:consumer-product-safety-governor` sign-off gate must
   clear: it checks the jurisdiction actually has an official spec-basis
   on file (never invent one). A jurisdiction with no spec-basis is a
   HARD hold at the governor node -- it never even reaches a human. This
   verification always escalates to a human for approval; it is never
   auto.
3. **Dispatch.** Before household goods can leave the distribution
   center, the `:consumer-product-safety-governor` sign-off gate runs
   the full HARD check set against the order's own ground truth: the
   spec-basis exists, the evidence checklist is complete, the
   counterparty's credit has been cleared, contract-terms are on file,
   IF this is a children's product it has a valid Children's Product
   Certificate on file (both the underlying lead/phthalate lab test and
   the certificate itself), the SKU has no active unresolved recall, the
   counterparty has passed sanctions screening, and the order has not
   already been dispatched. Any failure is a HARD hold that a human
   cannot override. If every check is clean, the proposal STILL always
   escalates to a human trading supervisor -- a `:delivery/dispatch`
   never auto-commits at any phase. On approval, the dispatch record is
   drafted (`<JURISDICTION>-DISPATCH-000001`) and the order's
   `:dispatched?` flag is set.
4. **Settle.** Once household goods have actually been dispatched, the
   invoice is settled (`:invoice/settle`): the money side of the trade,
   custody / financial transfer. The governor re-checks the spec-basis,
   the evidence completeness, that the SKU STILL has no active
   unresolved recall (a recall can be discovered AFTER a clean dispatch
   -- see below), the sanctions screening, and that this order's invoice
   has not already been settled. As with the dispatch, a clean invoice
   STILL always escalates to a human trading supervisor --
   `:invoice/settle` never auto-commits. On approval the invoice record
   is drafted (`<JURISDICTION>-INVOICE-000001`) and the order's
   `:invoiced?` flag is set.
5. **Audit.** The verification, the dispatch sign-off, the dispatch
   record, the invoice sign-off, and the invoice record are all appended
   to the `:audit-ledger` -- immutable and exportable, so a counterparty
   or regulatory dispute can be traced back to the exact spec-basis
   citation, evidence checklist, and supervisor sign-off that authorized
   the dispatch and invoice. If something is wrong with the order (a
   credit deterioration, a sanctions hit, a missing Children's Product
   Certificate, a newly-discovered recall), that gets raised as a flag
   and routed through the escalation gate instead of being silently
   suppressed -- a dispatch or invoice for that order then waits on
   governor sign-off of the flag's resolution.

### A special case: what happens when a recall is discovered mid-lifecycle

Unlike credit-clearance, contract-on-file, or the Children's Product
Certificate (all checked once, at dispatch time, and never re-checked),
an active recall is a fact that can change AFTER a household-order has
already dispatched cleanly. If CPSC (or the wholesaler's own compliance
monitoring) later determines a SKU has a substantial product hazard
(Consumer Product Safety Act §15(b)), the compliance officer records
this by patching the order's `:recall-status` to `:open` -- an ordinary
`:order/intake` update, no special op required. From that point on, ANY
further `:delivery/dispatch` OR `:invoice/settle` proposal referencing
that SKU HARD-holds at the governor, un-overridable, until the
compliance officer records the recall's resolution (`:recall-status
:resolved`), at which point the order dispatches/invoices cleanly
again. This is a fundamentally different mechanic from the pre-shipment
Children's Product Certificate check above -- see `docs/adr/
0001-architecture.md` Decision 5 for the full reasoning.

## Feel the Decision Gate: `kbb -M:dev:run`

This vertical has no companion playable prototype. The fastest hands-on
way to feel why the `:consumer-product-safety-governor` gate exists is
the bundled demo, which walks one clean household-order through intake
→ verify → dispatch → settle (each dispatch/settle pausing for human
approval), then a fully-certified children's product through the same
lifecycle, then exercises every HARD-hold failure mode in isolation:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a counterparty whose credit has not been cleared → HOLD
  (`:credit-uncleared`),
- an order with no contract-terms on file → HOLD (`:contract-missing`),
- a counterparty that has not passed sanctions screening → HOLD
  (`:counterparty-sanctions-flag-unresolved`),
- a children's product with no valid Children's Product Certificate on
  file → HOLD (`:childrens-product-certificate-missing`),
- a SKU with an active, unresolved recall → HOLD
  (`:active-recall-unresolved`) -- then, on the SAME order, once the
  recall is patched to `:resolved`, the SAME order dispatches cleanly,
- a double dispatch of the same order → HOLD (`:already-dispatched`),
- a double invoice of the same order → HOLD (`:already-invoiced`).

Each HOLD settles at the governor node and never reaches a human
approver -- the same failure mode the audit ledger is built to catch and
the minimum trading controls above are built to prevent. It is not a
substitute for those controls, but it is the fastest way for a new
operator (or a reviewer) to feel, hands-on, why the gate exists before
touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded verification,
evidence-backed dispatch readiness (credit-clearance, contract-on-file,
Children's Product Certificate for children's products, active-recall
screening, sanctions-screening), and human review for every dispatch-
and invoice-affecting action.
