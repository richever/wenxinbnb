(ns r0_rich.invoice.crud
    (:use hiccup.core
          r0_rich.env
          r0_rich.pages.template_pg
          hiccup.core
          hiccup.util
          hiccup.page)
    (:require [clojure.java.jdbc.sql :as s] 
              [clj-time.coerce :as t]
              [clj-time.format :as f]
              [clojure.java.jdbc :as j]))

(defn index [session]
  (let [invoices (j/with-connection SQLDB 
                   (j/with-query-results rs ["select * from Invoice"] (doall rs)))]
    (if (:login session)
       (pages (list [:h2 "invoices"]
                    (for [invoice invoices]
                      [:div.row-fluid 
                       [:a.span1 {:href (str "/invoices/"(:id invoice))} (:id invoice)]
                       [:a.span3 {:href (str "/invoices/"(:id invoice))} (f/unparse (f/formatter "yy-MM-dd-hh") (t/from-long (:timestamp invoice)))]
         
                       [:a.span1 {:href (str "/invoices/"(:id invoice))} "$" (:total invoice)]
                       [:a.span1 {:href (str "/invoices/"(:id invoice))} (if (== 1 (:refund invoice)) "refunded" "sold")]
                       ])))
       (pages [:a {:href "/login"} "請登錄>>"]))))

(defn new [session]
  (let [taxs (j/with-connection SQLDB
               (j/with-query-results rs [(str "select * from Tax")] (doall rs)))]
  (if (and (:login session) (:invoice session))
    (pages 
      (list 
        [:form {:method "post" :action "/invoices/create" :novalidate "novalidate"}
           [:div.row-fluid 
             [:input.span1 {:value "plucode" :type "text" :readonly "readonly"}]
             [:input.span2 {:value "item name" :type "text" :readonly "readonly"}]
             [:input.span1 {:value "price" :type "text" :readonly "readonly"}]
             [:input.span1 {:value "quantity" :type "text" :readonly "readonly"}]]
           (for [invoice (:invoice session)] 
             [:div.row-fluid 
               [:input.span1 {:name (str "items["(name (first invoice))"][plucode]") :type "text" :readonly "readonly" :value (first invoice)}]
               [:input.span2 {:name (str "items["(name (first invoice))"][item_name]") :type "text" :readonly "readonly" :value (:item_name (second invoice))}]
               (if (= (:taxable (second invoice)) "1")
                 [:input.span1.price_change_with_tax {:name (str "items["(name (first invoice))"][price]") 
                              :type "number" :min 0 
                              :step (/ (:price (second invoice)) 10) 
                              :max (+ (* (:price (second invoice)) (:quantity (second invoice))) 0.001) 
                              :value (* (:price (second invoice)) (:quantity (second invoice)))}]
                 [:input.span1.price_change_without_tax {:name (str "items["(name (first invoice))"][price]") 
                              :type "number" :min 0 
                              :step (/ (:price (second invoice)) 10) 
                              :max (+ (* (:price (second invoice)) (:quantity (second invoice))) 0.001) 
                              :value (* (:price (second invoice)) (:quantity (:second invoice)))}])
               [:input.span1 {:name (str "items["(name (first invoice))"][quantity]")
                              :type "number" :readonly "readonly"
                              :step 1
                              :value (:quantity (second invoice))}]])
           [:div.row-fluid 
             [:input.span1.offset2 {:value "总数:" :type "text" :readonly "readonly"}] 
             [:input.input-large {:type "number" :readonly "readonly" :name "total"
                            :id "price_total"
                            :value 
                            (format "%.2f" (reduce + (for [invoice (:invoice session)] 
                                                       (double (* (:price (second invoice)) 
                                                                  (:quantity (second invoice)))))))}]] 
           [:div.row-fluid 
             [:lable.span2.offset1 "税收类型:"]
             [:select#tax_change.span3 {:name "tax"}
                [:option {:value 0 :selected "selected"} "no tax"]
              (for [tax taxs]
                [:option {:value (:rate tax)} (str (:name tax) " " (:rate tax))])]]
         [:div.row-fluid 
          [:input.span2.offset1 {:type "submit" :value "结帐"}]
          [:input.span2 {:type "reset" :value "重置"}]]]
        [:div.row-fluid
         [:a.span2.offset1 {:href "/items"} "继续添加"]]
        [:script {:src "/invoice_new.js"}]))
    (pages [:a {:href "/items"} "請先选择商品"]))))


(defn create [params session]
  (if (:login session)
    (do 
      (let [invoice (j/insert! SQLDB :Invoice 
                               {:timestamp (System/currentTimeMillis) 
                                :total (:total params) 
                                :tax (:tax params)
                                :refund 0})
            invoice_id ((keyword "last_insert_rowid()") (first invoice))] 
        (doseq [item (:items params)] 
          (let [origin_items (take (read-string (:quantity (second item))) (j/with-connection SQLDB 
                                     (j/with-query-results rs [(str "select * from Item where plucode = '" (:plucode (second item)) "';")] (doall rs))))]
                (doseq [origin_item origin_items] 
                  (j/insert! SQLDB :Item_sold {:item_name (:item_name origin_item)
                                   :item_type (:item_type origin_item)
                                   :plucode (:plucode origin_item)
                                   :price (/ (read-string (:price (second item))) 
                                             (read-string (:quantity (second item))))
                                   :cost (:cost origin_item)
                                   :refund 0
                                   :user_id (:user_id origin_item)
                                   :invoice_id invoice_id}) 
                  (j/delete! SQLDB :Item (s/where {:id (:id origin_item)}))))) 
        (let [sold_items (j/with-connection SQLDB 
                           (j/with-query-results rs [(str "select * from Item_sold where invoice_id = '" invoice_id "';")] (doall rs)))]
          {:body (pages 
                   (list 
                     [:h2 "以下商品成功结帐:"] 
                     (for [sold_item sold_items]
                       [:div.row-fluid
                        [:div.span1 "ID:" (:id sold_item)]
                        [:div.span1 "PLU:" (:plucode sold_item)]
                        [:div.span3 (:item_name sold_item)]
                        [:div.span1 "$" (:price sold_item)]]) 
                     [:a {:href (str "/invoices/" invoice_id)} "打印小票"]))
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :session (dissoc session :invoice)})))
    (pages [:a {:href "/login"} "請登錄>>"])))

(defn admin_invoice_pg [invoice]
  (let [sold_items (j/with-connection SQLDB
               (j/with-query-results rs [(str "select * from Item_sold where invoice_id = '" (:id invoice) "';")] (doall rs)))]
  (list [:h2.offset1 "invoice"] 
        [:div.printable
          [:h3 "Wenxin Bed & Breakfast"]
          [:div "www.wenxinbnb.ca"] [:br]
          [:div "sold items:"]
          (for [item sold_items]
            (list 
              [:div.row-fluid 
                [:span.span2 (:item_name item) "&nbsp;&nbsp;"] 
                [:span.span2 "plu" (:plucode item) "&nbsp;&nbsp;"] 
                [:span.span2 "$" (:price item) "&nbsp;&nbsp;"] 
                [:span.span2 (if (== 1 (:refund item)) "refunded" "sold")]]))
            [:div.row-fluid 
             [:span.span2 "total price: "] 
             [:span.span1 (double (:total invoice))]]
            [:div.row-fluid 
             [:span.span2 "invoice tax rate: "] 
             [:span.span1 (:tax invoice)]]
            [:div.row-fluid 
             [:span.span2 "timestamp: "] 
             [:span.span3 (t/from-long (:timestamp invoice))]]]
        [:a {:href "#" :onclick "printInvoice(this)"} "打印"] 
        [:br]
        [:div.row-fluid 
         [:div.span2 [:a {:href (str "/invoices/" (:id invoice) "/update")} "修改或退款"]]
         [:div.span2 [:a {:href (str "/invoices/" (:id invoice) "/remove")} "删除"]]
         [:div.span2 [:a {:href (str "/")} "返回"]]]
        )))

(defn invoice_pg [invoice]
  (let [sold_items (j/with-connection SQLDB
               (j/with-query-results rs [(str "select * from Item_sold where invoice_id = '" (:id invoice) "';")] (doall rs)))]
  (list [:h2.offset1 "invoice"] 
        [:h2 "Wenxin Bed & Breakfast"]
        [:div "sold items:"]
        (for [item sold_items]
          [:div.row-fluid 
           [:div.span3 (:item_name item)] 
           [:div.span2 (:plucode item)] 
           [:div.span2 "$" (:price item)]
           (if (== 1 (:refund item)) 
             [:div.span2 "returned"])])
        [:div.row-fluid 
         [:div.span2 "invoice total price: "] 
         [:div.span1 (double (:total invoice))]]
        [:div.row-fluid 
         [:div.span2 "invoice tax rate: "] 
         [:div.span1 (:tax invoice)]]
        [:div.row-fluid 
         [:div.span2 "timestamp: "] 
         [:div.span3 (t/from-long (:timestamp invoice))]]
        [:div.row-fluid 
         [:div.span2 [:a {:href (str "/")} "返回"]]])))

(defn show [id session]
  (let [invoice (first (j/with-connection SQLDB
               (j/with-query-results rs [(str "select * from Invoice where id = '" id "';")] (doall rs))))]
    (if (:login session) 
      (html5
         [:head 
          [:title "長亨POS系統"]]
          [:link {:href "/vendor/bootstrap/css/bootstrap.min.css" :rel "stylesheet" 
                  :type "text/css" :media "all"}]
          [:link {:href "/vendor/bootstrap/css/bootstrap-responsive.css" :rel "stylesheet" 
                  :type "text/css" :media "all"}]
          [:link {:href "/vendor/font-awesome/css/font-awesome.min.css" :rel "stylesheet" 
                  :type "text/css" :media "all"}]
          [:link {:href "/pos_style.css" :rel "stylesheet" 
                  :type "text/css" :media "all"}]
          (include-js "/invoice_printer.js")
         [:body
          [:div.row-fluid.content [:h1.offset1 "Wenxin Bed & Breakfast"]
            [:div.row-fluid (admin_invoice_pg invoice)]]])
      (html5
         [:head 
          [:title "長亨POS系統"]]
          (include-css "/vendor/bootstrap/css/bootstrap.min.css")
          (include-css "/vendor/bootstrap/css/bootstrap-responsive.css")
          (include-css "/vendor/font-awesome/css/font-awesome.min.css")
          (include-css "/pos_style.css")
         [:body
          [:div.row-fluid.content [:h1.offset1 "Wenxin Bed & Breakfast"]
            [:div.row-fluid (invoice_pg invoice)]]]))))

(defn update [id session]
  (let [invoice (first (j/with-connection SQLDB 
                           (j/with-query-results rs [(str "select * from Invoice where id = '" id "';")] (doall rs)))) 
        sold_items (j/with-connection SQLDB 
                     (j/with-query-results rs [(str "select * from Item_sold where invoice_id = '" (:id invoice) "';")] (doall rs)))]
  (if (:login session)
    (pages [:form.span10 {:action (str "/invoices/" id "/change") :method "post"}
           [:div.row-fluid
            [:lable.span2.offset1 "总价:"]
            [:input.span3 {:name "total" :type "text" :value (:total invoice)}]]
           [:div.row-fluid
            [:lable.span2.offset1 "税率:"]
            [:input.span3 {:name "tax" :type "text" :value (:tax invoice)}]]
           [:div.row-fluid
            [:lable.span2.offset1 "时间戳:"]
            [:input.span3 {:name "timestamp" :readonly "readonly" :type "text" :value (:timestamp invoice)}]]
           [:div.row-fluid
            [:h3 "已售商品:"] 
            [:div.row-fluid 
             [:input.span1 {:readonly "readonly" :type "text" :value "id"}] 
             [:input.span1 {:readonly "readonly" :type "text" :value "PLUcode"}] 
             [:input.span1 {:readonly "readonly" :type "text" :value "Name"}] 
             [:input.span1 {:readonly "readonly" :type "text" :value "Price"}]]
            (for [item sold_items]
              [:div.row-fluid
                [:input.span1 {:name (str "items["(:id item)"][id]") :readonly "readonly" :type "text" :value (:id item)}]
                [:input.span1 {:name (str "items["(:id item)"][plucode]") :readonly "readonly" :type "text" :value (:plucode item)}]
                [:input.span1 {:name (str "items["(:id item)"][item_name]") :readonly "readonly" :type "text" :value (:item_name item)}]
                [:input.span1 {:name (str "items["(:id item)"][price]") :readonly "readonly" :type "text" :value (:price item)}]
                [:input.span1 {:name (str "items["(:id item)"][cost]") :readonly "readonly" :type "hidden" :value (:cost item)}]
                [:input {:name (str "items["(:id item)"][user_id]") :readonly "readonly" :type "hidden" :value (:user_id item)}]
                [:input {:name (str "items["(:id item)"][invoice_id]") :readonly "readonly" :type "hidden" :value (:id invoice)}]
                [:input.span1 {:name (str "items["(:id item)"][refund]") :type "radio" :value 1 :checked (if (== 1 (:refund item)) "checked")} "退货"]
                [:input.span1 {:name (str "items["(:id item)"][refund]") :type "radio" :value 0 :checked (if (== 0 (:refund item)) "checked")} "保留"]
               ])]
           [:div.row-fluid
            [:input.span1.offset1 {:type "submit" :value "修改"}]]])
    (pages [:a {:href "/login"} "請登錄>>"]))))

(defn change [params session]
  (if (:login session)
    (do 
      (j/update! SQLDB :Invoice (dissoc params :items) (s/where {:id (:id params)}))
      (doseq [aitem (:items params)]
        (let [item (second aitem)
              update_item (j/update! SQLDB :Item_sold item (s/where {:id (:id item)}))]))
      (pages (list [:h2 "修改成功."]
                   [:script (str "window.location.replace(\"/invoices/" (:id params) "\");")])))
    (pages [:a {:href "/login"} "請登錄>>"])))

(defn aremove [id session]
  (if (:login session)
    (let [sold_items (j/with-connection SQLDB 
                     (j/with-query-results rs [(str "select * from Item_sold where invoice_id = '" id "';")] (doall rs)))] 
      (do (j/delete! SQLDB :Invoice (s/where {:id id})) 
          (doseq [item sold_items] 
            (j/delete! SQLDB :Item_sold (s/where {:id (:id item)})))
          (pages [:div (str id "号invoice删除成功.")])))
    (pages [:a {:href "/login"} "請登錄>>"])))
