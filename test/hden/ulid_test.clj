(ns hden.ulid-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [hden.ulid :refer [ulid with-context]])
  (:import
   (java.time Clock Instant ZoneOffset)
   (java.util Random)))

(def ^:private alphabet "0123456789ABCDEFGHJKMNPQRSTVWXYZ")
(def ^:private max-timestamp 281474976710655)

(defn- fixed-clock [millis]
  (Clock/fixed (Instant/ofEpochMilli millis) ZoneOffset/UTC))

(defn- sequence-clock [& values]
  (let [remaining (atom (seq values))]
    (proxy [Clock] []
      (getZone [] ZoneOffset/UTC)
      (withZone [zone] (Clock/system zone))
      (instant []
        (if-some [value (first @remaining)]
          (Instant/ofEpochMilli value)
          (throw (IllegalStateException. "Clock sequence exhausted"))))
      (millis []
        (if-some [value (first @remaining)]
          (do (swap! remaining next) value)
          (throw (IllegalStateException. "Clock sequence exhausted")))))))

(defn- fixed-random [value]
  (proxy [Random] []
    (nextBytes [bytes]
      (dotimes [i (alength bytes)]
        (aset-byte bytes i (unchecked-byte value))))))

(defn- encode-base32 [n length]
  (loop [n (bigint n)
         remaining length
         encoded ()]
    (if (zero? remaining)
      (apply str encoded)
      (let [digit (int (mod n 32))]
        (recur (quot n 32)
               (dec remaining)
               (conj encoded (.charAt alphabet digit)))))))

(deftest generates-canonical-ulid
  (testing "the default API returns a canonical 26-character ULID"
    (let [value (ulid)]
      (is (= 26 (count value)))
      (is (re-matches #"[0-9A-HJKMNP-TV-Z]{26}" value)))))

(deftest encodes-a-known-timestamp
  (with-context {:clock  (fixed-clock 1469918176385)
                 :random (fixed-random 0)}
    (is (= "01ARYZ6S410000000000000000" (ulid)))))

(deftest increments-randomness-within-the-same-millisecond
  (with-context {:clock  (fixed-clock 0)
                 :random (fixed-random 0)}
    (is (= "00000000000000000000000000" (ulid)))
    (is (= "00000000000000000000000001" (ulid)))
    (is (= "00000000000000000000000002" (ulid)))))

(deftest samples-fresh-randomness-when-time-advances
  (with-context {:clock  (sequence-clock 0 0 1)
                 :random (fixed-random 0)}
    (is (= "00000000000000000000000000" (ulid)))
    (is (= "00000000000000000000000001" (ulid)))
    (is (= "00000000010000000000000000" (ulid)))))

(deftest preserves-monotonicity-when-the-clock-moves-backwards
  (with-context {:clock (sequence-clock 1 0 -1)
                 :random (fixed-random 0)}
    (is (= "00000000010000000000000000" (ulid)))
    (is (= "00000000010000000000000001" (ulid)))
    (is (= "00000000010000000000000002" (ulid)))))

(deftest rejects-an-out-of-range-clock-on-first-generation
  (testing "negative epoch milliseconds"
    (with-context {:clock  (fixed-clock -1)
                   :random (fixed-random 0)}
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Timestamp is outside the 48-bit ULID range"
                            (ulid)))))
  (testing "timestamp greater than 2^48 - 1"
    (with-context {:clock  (fixed-clock (inc max-timestamp))
                   :random (fixed-random 0)}
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Timestamp is outside the 48-bit ULID range"
                            (ulid))))))

(deftest encodes-the-largest-valid-timestamp
  (with-context {:clock  (fixed-clock max-timestamp)
                 :random (fixed-random 0)}
    (is (= "7ZZZZZZZZZ0000000000000000" (ulid)))))

(deftest accepts-the-largest-valid-ulid-and-fails-on-the-next-same-millisecond
  (with-context {:clock (fixed-clock max-timestamp)
                 :random (fixed-random 0xff)}
    (is (= "7ZZZZZZZZZZZZZZZZZZZZZZZZZ" (ulid)))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"ULID randomness overflow"
                          (ulid)))))

(deftest concurrent-generation-is-linearizable
  (with-context {:clock (fixed-clock 0)
                 :random (fixed-random 0)}
    (let [values (->> (range 100)
                      (mapv (fn [_] (future (ulid))))
                      (mapv deref))
          expected (set (map #(str "0000000000" (encode-base32 % 16))
                             (range 100)))]
      (is (= 100 (count (set values))))
      (is (= expected (set values))))))

(deftest nested-contexts-have-independent-monotonic-state
  (with-context {:clock  (fixed-clock 0)
                 :random (fixed-random 0)}
    (is (= "00000000000000000000000000" (ulid)))
    (is (= "00000000000000000000000001" (ulid)))
    (with-context {}
      (is (= "00000000000000000000000000" (ulid))))
    (is (= "00000000000000000000000002" (ulid)))))
