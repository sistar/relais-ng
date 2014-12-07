(ns relais-ng.utils)

;; shutdown hooks

(defn add-shutdown-hook
  [f]
  (.addShutdownHook (java.lang.Runtime/getRuntime)
                    (Thread. ^Runnable f)))

(defmacro on-shutdown
  [& body]
  `(add-shutdown-hook (fn [] ~@body)))

(defn parse-long
  [^String s]
  (Long/valueOf s))

(defn parse-longs
  [& s]
  (mapv parse-long s))

(defmacro logt
  "Time the evaluation of the body and log it along with msg"
  [msg & body]
  `(let [start# (System/currentTimeMillis)
         result# (do ~@body)]
     (clojure.tools.logging/info ~msg "took" (format "%.2fs" (* (- (System/currentTimeMillis) start#) 1e-3)))
     result#))

(defmacro loge
  [msg & body]
  `(try
     ~@body
     (catch Exception e#
       (clojure.tools.logging/error e# "failed while" ~msg))))