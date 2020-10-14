;; Copyright © 2020, JUXT LTD.

(ns juxt.spin.handler-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [juxt.spin.alpha.resource :as r]
   [juxt.spin.alpha.handler :as handler]
   [ring.mock.request :refer [request]])
  (:import (java.util.logging Logger Level Handler)))

(def ^:dynamic *log-records* [])

(defn with-log-capture [f]
  (binding [*log-records* (atom [])]
    (let [juxt-logger (Logger/getLogger "juxt")
          h (proxy [Handler] []
              (close [] nil)
              (flush [] nil)
              (publish [lr] (swap! *log-records* conj lr)))
          old-level (.getLevel juxt-logger)]
      (try
        (.addHandler juxt-logger h)
        (.setLevel juxt-logger Level/FINEST)
        (f)
        (finally
          (.setLevel juxt-logger old-level)
          (.removeHandler juxt-logger h))))))

(use-fixtures :each with-log-capture)

(deftest nil-resource-test
  ;; Principle of Least Surprise: Should return 404 if nil resource
  (let [h (handler/handler
           (reify
             r/ResourceLocator
             (locate-resource [_ uri request]
               (cond
                 (.endsWith uri "/connor") {:name "Connor"}))))]

    (is (= 200 (:status (h (request :get "/connor")))))
    (is (= 404 (:status (h (request :get "/malcolm")))))))

(deftest method-not-allowed-test
  (let [*response (promise)
        h (#'handler/invoke-method
           nil
           nil
           #{:get :head :put :post :delete :options})]
    (h (request :post "/")
       (fn [r] (deliver *response r))
       (fn [_]))
    (let [response (deref *response 0 :timeout)]
      (is (= 405 (:status response)))
      (is (= "GET, HEAD" (get-in response [:headers "allow"])))
      response)))

(deftest known-methods-test
  (is (= 6 (count (handler/known-methods)))))


;; TODO: Test other combinations, including where AllowedMethods is implemented.

(deftest content-length-in-response-test
  (let [h (handler/handler
           (reify
             r/ResourceLocator
             (locate-resource [_ uri request]
               {:juxt.http/content-length 1000})))]
    (let [response (h (request :get "/connor"))]
      (is (= "1000" (get-in response [:headers "content-length"]))))))