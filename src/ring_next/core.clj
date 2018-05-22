(ns ring-next.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :refer :all]
            [ring.middleware.keyword-params :refer :all]
            [ring.middleware.cookies :refer :all]
            [ring.middleware.multipart-params :refer :all]
            [ring.util.response :as response]
            [clojure.pprint :as pprint])
  (:gen-class))


;; ----------------------------------------------------------------------------------------------------
;; Reusing the handler from the Echo project. This is our 'main' handler
(defn debug-return-request [request]
  (let [s (with-out-str (pprint/pprint (conj request {:body (slurp (:body request))})))]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body   s}))

;; ----------------------------------------------------------------------------------------------------
;; Middleware

;; You can add items to the request ahead of your main handler
;; Here we are adding a new field :message with a string which has been passed in as a parameter
;;
;; (wrap-request-method-message handler "This is a test message!!")
;;
(defn wrap-request-method-message [handler s]
  (fn [request]
    (handler (assoc request :message s))))

;; Log/print some debug messages during a handler's execution
;;
;; (wrap-debug-print-request handler)
;;
(defn wrap-debug-print-request [handler]
  (fn [request]
    (pprint/pprint (conj request {:body (slurp (:body request))}))
    (handler request)))

;; Ignore favicon.ico requests
;;
;; (wrap-ignore-favicon-request [handler]
;;
(defn wrap-ignore-favicon-request [handler]
  (fn [request]
    (if (= (:uri request) "/favicon.ico")
      {:status 404}
      (handler request))))

;; ----------------------------------------------------------------------------------------------------
;; Static File
;;
;; Library: ring.util.response
 
(defn static-file
  "Return a static file"
  [filename]
  (response/resource-response filename {:root "public"}))

;; ----------------------------------------------------------------------------------------------------
;; Cookies
;;
;; Middleware: wrap-cookies

(defn get-cookie [request cookie-info]
  (:value (get (:cookies request) (:name cookie-info))))

(defn set-cookie [response request cookie-info val]
  (response/set-cookie response (:name cookie-info) val {:path (:path cookie-info)}))
   
(defn clear-cookie [response request cookie-info]
  (response/set-cookie response (:name cookie-info) "" {:max-age 1 :path (:path cookie-info)}))

(defn cookie
  "Handle cookie request. If GET then read and return the cookie value
else if POST then accept posted value and set the cookie."
  [request]
  (let [cookie-command (fn [s] (keyword (get (clojure.string/split s #"/") 2)))
        cmd (cookie-command (:uri request))          ;; :get, :set, :clear
        cookie-info {:name "ring-next-cookie" :path "/cookie"}]
    (if (and (= cmd :set) (= :post (:request-method request)))    ;; only allow set if posted
      (let [val (:value (:params request))]                       ;; (wrap-params (wrap-keyword-params handler))
        (set-cookie (response/response "Set cookie") request cookie-info val))
      (if (= cmd :clear)
        (clear-cookie (response/response "Clear cookie") request cookie-info)
        (response/response (str "Cookie value is '" (get-cookie request cookie-info) "'"))))))


;; ----------------------------------------------------------------------------------------------------
;; File
;;
;; https://github.com/ring-clojure/ring/wiki/File-Uploads
;;
;; :params
;;  {"file" {:filename     "words.txt"
;;           :content-type "text/plain"
;;           :tempfile     #object[java.io.File ...]
;;           :size         51}}

(defn upload-file [request cookie-info]
  (let [original-filename (:filename (:file (:params request)))
        tempfile (:tempfile (:file (:params request)))]
    ;; save tempfile location in cookie
    (set-cookie (response/response "File uploaded") request cookie-info (.getPath tempfile))))

(defn download-file [request cookie-info]
  ;; read file from tempfile location stored in cookie
  (let [filepath (get-cookie request cookie-info)]
    (response/file-response filepath)))

(defn file
  "Handle file request. If GET then read the file and return its contents
else if POST then accept the posted file and save it."
  [request]
  (let [file-command (fn [s] (keyword (get (clojure.string/split s #"/") 2)))
        cmd (file-command (:uri request))
        cookie-info {:name "ring-next-file" :path "/file"}]

    (if (and (= cmd :upload) (= :post (:request-method request)))   ;; only allow upload if posted
      (upload-file request cookie-info)
      (download-file request cookie-info))))


;; ----------------------------------------------------------------------------------------------------
;; Routes

(defn routes [request]
  (let [uri (:uri request)]
    (case uri
      ;; static file
      "/" (static-file "index.html")
      "/index.html" (static-file "index.html")

      ;; cookie
      "/cookie" (static-file "cookie.html")
      "/cookie/get" (cookie request)
      "/cookie/set" (cookie request)
      "/cookie/clear" (cookie request)

      ;; file
      "/file" (static-file "file.html")
      "/file/upload" (file request)
      "/file/download" (file request)

      ;; default to our main 'echo' handler
      (debug-return-request request))))


(def app
  ;; Initial call chain
  ;; (wrap-ignore-favicon-request (wrap-multipart-params (wrap-cookies (wrap-params (wrap-keyword-params (wrap-request-method-message (wrap-debug-print-request routes) "This is a test message!!"))))))

  ;; Using the threading macro
  (-> routes
      wrap-debug-print-request
      (wrap-request-method-message "This is a test message!!")
      wrap-keyword-params                                            ;; this needs to be 'after' wrap-params so there is a :params field for it to its work on
      wrap-params
      wrap-cookies
      wrap-multipart-params       
      wrap-ignore-favicon-request)
  )


(defn -main []
  (jetty/run-jetty app {:port 3000}))
