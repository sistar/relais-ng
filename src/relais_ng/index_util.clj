(ns relais-ng.index-util)
(defn next-index
  [kw group]
  (let [existing (map kw group)
        max-existing (if (empty? existing) -1 (apply max existing))
        ]
    (+ max-existing 1)))

(defn valid-index
  "return unused index"
  [kw subject group]
  (let [v (kw subject)
        existing (map kw group)]
    (if (and (some? v) (not (some #{v} existing))) v (next-index kw group))))
