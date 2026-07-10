# Governance

`cloud-itonami-isic-4649` is an OSS open-business blueprint for wholesale of
other household goods (housewares, small appliances, furniture, toys and
similar consumer goods not classified elsewhere).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a household-order whose jurisdiction has no official spec-basis can never
  be verified, dispatched or invoiced.
- the Consumer Product Safety Governor remains independent of the advisor.
- hard governor violations (a fabricated spec-basis, incomplete
  counterparty-diligence evidence, an uncleared counterparty credit, a
  missing contract, a missing Children's Product Certificate on a children's
  product, an open unresolved CPSC-style recall, an unresolved OFAC-style
  sanctions flag, a double dispatch, or a double invoice) cannot be
  overridden by human approval.
- `childrens-product-certificate-missing` and `active-recall-unresolved`
  remain two SEPARATE checks -- a pre-shipment product-certification gate and
  a post-hoc discovered-defect flag gate respectively -- never collapsed into
  one rule.
- every intake, safety verification, dispatch, settlement and hold is
  auditable.
- counterparty, credit, product-testing, certificate and recall data stays
  outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:
- bypassing dispatch or invoice-settlement policy checks
- mishandling counterparty, credit, product-testing, certificate, or recall
  data
- misrepresenting certification or recall status
- failing to respond to security incidents
