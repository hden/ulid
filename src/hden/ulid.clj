(ns hden.ulid
  (:import
   (java.math BigInteger)
   (java.security SecureRandom)
   (java.time Clock)
   (java.util Random)))

(def ^:private ^String alphabet
  "0123456789ABCDEFGHJKMNPQRSTVWXYZ")

(def ^:private ^BigInteger base32-mask
  (BigInteger/valueOf 31))

(def ^:private ^BigInteger max-randomness
  (.subtract (.shiftLeft BigInteger/ONE 80) BigInteger/ONE))

(def ^:private max-timestamp
  281474976710655)

(defn- default-context []
  {:clock  (Clock/systemUTC)
   :random (SecureRandom.)
   :state  (atom nil)})

(def ^:dynamic ^:no-doc *context*
  "The dynamically scoped ULID generation context.

   Most callers should use `ulid`. Tests may override dependencies with
   `with-context`."
  (default-context))

(defmacro with-context
  "Evaluates body with optional `:clock` and `:random` overrides.

   `:clock` must be a java.time.Clock and `:random` a java.util.Random.
   Each context receives an independent monotonic state. Nested contexts
   inherit unspecified dependencies from their parent context."
  [overrides & body]
  `(binding [*context* (assoc (merge *context* ~overrides)
                              :state
                              (atom nil))]
     ~@body))

(defn- random-80-bits ^BigInteger [^Random random]
  (let [bytes (byte-array 10)]
    (.nextBytes random bytes)
    (BigInteger. 1 bytes)))

(defn- validate-timestamp! [timestamp]
  (when-not (<= 0 timestamp max-timestamp)
    (throw
      (ex-info "Timestamp is outside the 48-bit ULID range"
               {:timestamp timestamp
                :minimum   0
                :maximum   max-timestamp})))
  timestamp)

(defn- increment-randomness ^BigInteger [^BigInteger randomness]
  (let [next-randomness (.add randomness BigInteger/ONE)]
    (when (pos? (.compareTo next-randomness max-randomness))
      (throw
        (ex-info "ULID randomness overflow"
                 {:randomness randomness
                  :maximum    max-randomness})))
    next-randomness))

(defn- next-state [old timestamp entropy]
  (if (or (nil? old) (> timestamp (:timestamp old)))
    {:timestamp  (validate-timestamp! timestamp)
     :randomness @entropy}
    {:timestamp  (:timestamp old)
     :randomness (increment-randomness (:randomness old))}))

(defn- generate-state! [{:keys [clock random state]}]
  (let [timestamp (.millis ^Clock clock)
        entropy (delay (random-80-bits random))]
    (loop []
      (let [old @state
            candidate (next-state old timestamp entropy)]
        (if (compare-and-set! state old candidate)
          candidate
          (recur))))))

(defn- encode-base32 ^String [^BigInteger value length]
  (let [encoded (char-array length)]
    (loop [index (dec length)
           ^BigInteger value value]
      (if (neg? index)
        (String. encoded)
        (let [digit (.intValue (.and value base32-mask))]
          (aset-char encoded index (.charAt alphabet digit))
          (recur (dec index) (.shiftRight value 5)))))))

(defn ulid
  "Returns a canonical, monotonically increasing ULID string."
  []
  (let [{:keys [timestamp randomness]} (generate-state! *context*)]
    (str (encode-base32 (BigInteger/valueOf timestamp) 10)
         (encode-base32 randomness 16))))
