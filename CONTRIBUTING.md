# Contributing

`cloud-itonami-isic-4649` accepts contributions to the OSS blueprint, the
Consumer Product Safety Governor, decision-rule tests, documentation and
operator model.

## Development
The capability layer is SELF-CONTAINED. There is no pre-existing bespoke
household-goods-wholesale capability library to wrap; the counterparty-credit
/ contract-on-file / children's-product-certificate / active-recall /
sanctions-screening checks live directly in `housewaretrade.governor`. This
repo holds the business blueprint, the langgraph-clj actor and the operator
contracts.

```bash
clojure -M:dev:test
clojure -M:lint
```

## Rules
- Do not commit real counterparty, credit, product-testing, certificate, or
  recall data.
- Keep physical dispatch and invoice settlement behind the Consumer Product
  Safety Governor.
- Treat safety-compliance workflows as high-risk: add tests for spec-basis,
  evidence completeness, credit clearance, contract-on-file, children's-
  product certification, active-recall status, sanctions screening and audit
  logging.
- Keep `childrens-product-certificate-missing` type-gated on
  `:childrens-product?` -- do not silently apply it to a general household
  good, and do not fold `active-recall-unresolved` into it (see
  `docs/adr/0001-architecture.md` Decisions 4-5 for why these are separate).
- Never fabricate a jurisdiction's consumer-product-safety requirements in
  `housewaretrade.facts` -- cite a real official source or leave the
  jurisdiction out of the catalog.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which governor invariant is
affected, how it was tested, whether operator or certification docs need
updates.
