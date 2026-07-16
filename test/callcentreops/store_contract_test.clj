(ns callcentreops.store-contract-test
  "Contract tests for `callcentreops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [callcentreops.store :as store]))

(deftest mem-store-campaign-lookup
  (testing "MemStore can store and retrieve campaigns by ID (string keys)"
    (let [campaigns {"c1" {:campaign-id "c1" :client "Alice's Bakery Support" :registered? true :verified? true}}
          s (store/mem-store campaigns)]
      (is (some? (store/campaign s "c1")))
      (is (nil? (store/campaign s "c99"))))))

(deftest mem-store-all-campaigns
  (testing "MemStore returns all campaigns in sorted order"
    (let [campaigns {"c2" {:campaign-id "c2" :client "Bob's Garage Outbound"}
                      "c1" {:campaign-id "c1" :client "Alice's Bakery Support"}
                      "c3" {:campaign-id "c3" :client "Carol's Clinic Support"}}
          s (store/mem-store campaigns)
          all-c (store/all-campaigns s)]
      (is (= 3 (count all-c)))
      (is (= "c1" (:campaign-id (first all-c))))
      (is (= "c3" (:campaign-id (last all-c)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-ops-log
  (testing "MemStore commit-record! appends to ops-log"
    (let [s (store/mem-store {})
          record {:op :log-call-record :campaign-id "c1" :value {:calls-handled 10}}]
      (is (= 0 (count (store/ops-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/ops-log s))))
      (is (= record (first (store/ops-log s)))))))

(deftest mem-store-with-campaigns
  (testing "MemStore with-campaigns replaces the campaign directory"
    (let [s (store/mem-store {})
          new-campaigns {"c1" {:campaign-id "c1" :client "Alice's Bakery Support"}}]
      (is (= 0 (count (store/all-campaigns s))))
      (store/with-campaigns s new-campaigns)
      (is (= 1 (count (store/all-campaigns s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo campaigns"
    (let [s (store/seed-db)]
      (is (> (count (store/all-campaigns s)) 0))
      (is (some? (store/campaign s "campaign-1")))
      (is (some? (store/campaign s "campaign-2")))
      (is (some? (store/campaign s "campaign-3"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for campaign-id"
    (let [demo (store/demo-data)
          campaigns (:campaigns demo)]
      (doseq [[k v] campaigns]
        (is (string? k) "keys must be strings")
        (is (string? (:campaign-id v)) "campaign-id must be string")
        (is (= k (:campaign-id v)) "key must match campaign-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
