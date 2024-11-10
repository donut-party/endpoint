(ns donut.endpoint.encoding
  (:require
   [camel-snake-kebab.core :as csk]
   [cognitect.transit :as transit]
   [muuntaja.core :as m])
  (:import
   (java.time
    Duration
    Instant
    LocalDate
    LocalDateTime
    LocalTime
    OffsetDateTime
    OffsetTime
    Period
    ZonedDateTime)))

(defn time-name
  [class-kebab]
  (str "time/" class-kebab))

(defmacro mk-time-classes
  [& class-names]
  (->> (for [class-name class-names]
         `{:transit-name (time-name ~(csk/->kebab-case-string class-name))
           :class ~class-name
           :reader (fn [x#] (~(symbol (name class-name) "parse") x#))})
       (into [])))

(def time-classes
  (mk-time-classes Duration
                   Instant
                   LocalDate
                   LocalDateTime
                   LocalTime
                   OffsetDateTime
                   OffsetTime
                   Period
                   ZonedDateTime))

(def write-handlers
  (into {}
        (for [{:keys [transit-name class]} time-classes]
          [class (transit/write-handler (constantly transit-name) str)])))

(def read-handlers
  (into {} (for [{:keys [transit-name reader]} time-classes]
             [transit-name (transit/read-handler reader)]))) ; omit "time/" for brevity

(def transit-format "application/transit+json")

(def default-muuntaja-instance
  (m/create (-> m/default-options
                (update-in [:formats transit-format :encoder-opts :handlers]
                           merge
                           write-handlers)
                (update-in [:formats transit-format :decoder-opts :handlers]
                           merge
                           read-handlers))))
