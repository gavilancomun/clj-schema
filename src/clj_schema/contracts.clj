(ns clj-schema.contracts
  "Unobtrusively apply contracts to vars that hold functions"
  (:use [clj-schema.schema :refer [def-map-schema optional-path schema? sequence-of]]
        [clj-schema.simple-schemas :refer [Anything]]
        [clj-schema.validation :refer [validation-errors]])
  (:require [robert.hooke :as hooke]))


(def-map-schema :loose contract-schema
  [(optional-path [:var]) var?
   (optional-path [:input-schema]) Anything
   (optional-path [:input-schema-on-failure]) fn?
   (optional-path [:output-schema]) Anything
   (optional-path [:output-schema-on-failure]) fn?])

(defn- schema-checker-fn [{:keys [var
                                  input-schema
                                  input-schema-on-failure
                                  input-schema-on-success
                                  output-schema
                                  output-schema-on-failure
                                  output-schema-on-success]}]
  (fn [f & args]
    (let [errors (and input-schema (validation-errors input-schema args))]
      (if (seq errors)
        (if input-schema-on-failure
          (input-schema-on-failure var (vec args) errors)
          (throw (Exception. (str "Errors found in inputs, " (vec args) ", to " var ": " errors))))
        (when input-schema-on-success
          (input-schema-on-success var (vec args)))))

    (let [result (apply f args)
          errors (and output-schema (validation-errors output-schema result))]
      (if (seq errors)
        (if output-schema-on-failure
          (output-schema-on-failure var result errors)
          (throw (Exception. (str "Errors found in outputs, " result ", from " var ": " errors))))
        (when output-schema-on-success
          (output-schema-on-success var result)))
      result)))

(defn add-contracts!
  ""
  [contracts]
  (when-let [errors (seq (validation-errors (sequence-of contract-schema) contracts))]
    (throw (Exception. (str "contracts were not valid: " contracts errors))))
  (doseq [c contracts]
    (hooke/add-hook (:var c) ::contract (schema-checker-fn c))))

(defn remove-contracts!
  ""
  [contracts]
  (when-let [errors (seq (validation-errors (sequence-of contract-schema) contracts))]
    (throw (Exception. (str "contracts were not valid: " contracts errors))))
  (doseq [c contracts]
    (hooke/remove-hook (:var c) ::contract)))