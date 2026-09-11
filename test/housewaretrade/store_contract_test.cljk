(ns housewaretrade.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [housewaretrade.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "USA" (:jurisdiction (store/household-order s "ho-1"))))
      (is (= "Akita Household Goods Trading Co" (:counterparty (store/household-order s "ho-1"))))
      (is (= :cookware (:product-category (store/household-order s "ho-1"))))
      (is (false? (:childrens-product? (store/household-order s "ho-1"))) "ho-1 is a general household good")
      (is (= :none (:recall-status (store/household-order s "ho-1"))) "ho-1 has no recall history")
      (is (= "ATL" (:jurisdiction (store/household-order s "ho-2"))))
      (is (false? (:credit-cleared? (store/household-order s "ho-3"))) "ho-3 credit not cleared")
      (is (nil? (:contract-terms (store/household-order s "ho-4"))) "ho-4 no contract-terms")
      (is (false? (:sanctions-screened? (store/household-order s "ho-5"))) "ho-5 sanctions not screened")
      (is (true? (:childrens-product? (store/household-order s "ho-6"))) "ho-6 is a children's product")
      (is (true? (:childrens-product-certificate-on-file? (store/household-order s "ho-6"))) "ho-6 has a CPC on file")
      (is (true? (:childrens-product? (store/household-order s "ho-7"))) "ho-7 is a children's product")
      (is (false? (:childrens-product-certificate-on-file? (store/household-order s "ho-7"))) "ho-7 has no CPC on file")
      (is (true? (:lead-phthalate-tested? (store/household-order s "ho-7"))) "ho-7 was lab-tested but has no CPC filed")
      (is (= :open (:recall-status (store/household-order s "ho-8"))) "ho-8 has an active unresolved recall")
      (is (false? (:childrens-product? (store/household-order s "ho-8"))) "ho-8 is a general household good, not a children's product")
      (is (false? (:dispatched? (store/household-order s "ho-1"))))
      (is (false? (:invoiced? (store/household-order s "ho-1"))))
      (is (= ["ho-1" "ho-2" "ho-3" "ho-4" "ho-5" "ho-6" "ho-7" "ho-8"]
             (mapv :id (store/all-household-orders s))))
      (is (nil? (store/assessment-of s "ho-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/invoice-history s)))
      (is (zero? (store/next-dispatch-sequence s "USA")))
      (is (zero? (store/next-invoice-sequence s "USA")))
      (is (false? (store/household-order-already-dispatched? s "ho-1")))
      (is (false? (store/household-order-already-invoiced? s "ho-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :order/upsert
                                 :value {:id "ho-1" :counterparty "Akita Household Goods Trading Co"}})
        (is (= "Akita Household Goods Trading Co" (:counterparty (store/household-order s "ho-1"))))
        (is (= "USA" (:jurisdiction (store/household-order s "ho-1"))) "unrelated field preserved"))
      (testing "safety-assessment payloads commit and read back"
        (store/commit-record! s {:effect :safety-assessment/set :path ["ho-1"]
                                 :payload {:jurisdiction "USA" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "USA" :checklist ["a" "b"]} (store/assessment-of s "ho-1"))))
      (testing "household-goods dispatch drafts a record and advances the dispatch sequence"
        (store/commit-record! s {:effect :order/mark-dispatched :path ["ho-1"]})
        (is (= "USA-DISPATCH-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "household-goods-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:dispatched? (store/household-order s "ho-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "USA")))
        (is (true? (store/household-order-already-dispatched? s "ho-1"))))
      (testing "invoice settlement drafts a record and advances the invoice sequence"
        (store/commit-record! s {:effect :order/mark-invoiced :path ["ho-1"]})
        (is (= "USA-INVOICE-000000" (get (first (store/invoice-history s)) "record_id")))
        (is (= "household-goods-invoice-draft" (get (first (store/invoice-history s)) "kind")))
        (is (true? (:invoiced? (store/household-order s "ho-1"))))
        (is (= 1 (count (store/invoice-history s))))
        (is (= 1 (store/next-invoice-sequence s "USA")))
        (is (true? (store/household-order-already-invoiced? s "ho-1"))))
      (testing "recall-status patches merge like any other upsert field"
        (store/commit-record! s {:effect :order/upsert :value {:id "ho-8" :recall-status :resolved}})
        (is (= :resolved (:recall-status (store/household-order s "ho-8"))))
        (is (= "Harrow Appliance Traders Co" (:counterparty (store/household-order s "ho-8"))) "unrelated field preserved"))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/household-order s "nope")))
    (is (= [] (store/all-household-orders s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/invoice-history s)))
    (is (zero? (store/next-dispatch-sequence s "USA")))
    (is (zero? (store/next-invoice-sequence s "USA")))
    (store/with-household-orders s {"x" {:id "x" :order-id "HO-X" :product-category :cookware
                                         :sku "HG-SKU-X"
                                         :counterparty "c" :price 4200.00
                                         :contract-terms "FOB distribution center, net 30 days"
                                         :credit-cleared? true :sanctions-screened? true
                                         :childrens-product? false
                                         :lead-phthalate-tested? false
                                         :childrens-product-certificate-on-file? false
                                         :recall-status :none
                                         :dispatched? false :invoiced? false
                                         :jurisdiction "USA" :status :intake
                                         :dispatch-number nil :invoice-number nil}})
    (is (= "c" (:counterparty (store/household-order s "x"))))))
