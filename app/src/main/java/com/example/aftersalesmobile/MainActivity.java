package com.example.aftersalesmobile;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SessionManager session;
    private ApiClient api;
    private FrameLayout content;
    private View appBar;
    private View loadingOverlay;
    private TextView pageTitle;
    private TextView pageSubtitle;
    private BottomNavigationView bottomNavigation;
    private int currentPage = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        session = new SessionManager(this);
        api = new ApiClient(session);
        content = findViewById(R.id.content);
        appBar = findViewById(R.id.appBar);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        pageTitle = findViewById(R.id.pageTitle);
        pageSubtitle = findViewById(R.id.pageSubtitle);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        findViewById(R.id.refreshButton).setOnClickListener(v -> loadCurrentPage());
        bottomNavigation.setOnItemSelectedListener(item -> {
            currentPage = item.getItemId();
            loadCurrentPage();
            return true;
        });

        if (session.isLoggedIn()) verifySession(); else showLogin();
    }

    private void verifySession() {
        runRequest(() -> api.get("auth/me"), ignored -> showShell(), true);
    }

    private void showLogin() {
        appBar.setVisibility(View.GONE);
        bottomNavigation.setVisibility(View.GONE);
        View login = getLayoutInflater().inflate(R.layout.screen_login, content, false);
        content.removeAllViews();
        content.addView(login);
        TextInputEditText username = login.findViewById(R.id.usernameInput);
        TextInputEditText password = login.findViewById(R.id.passwordInput);
        TextView serverState = login.findViewById(R.id.serverState);
        login.findViewById(R.id.loginButton).setOnClickListener(v -> login(username, password));
        login.findViewById(R.id.registerButton).setOnClickListener(v -> showRegisterDialog());
        password.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                login(username, password);
                return true;
            }
            return false;
        });
        executor.execute(() -> {
            try {
                api.get("health");
                runOnUiThread(() -> {
                    serverState.setText("服务器连接正常 · " + ApiClient.BASE_URL);
                    serverState.setTextColor(ContextCompat.getColor(this, R.color.status_success));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    serverState.setText("无法连接服务器，请检查网络或服务状态");
                    serverState.setTextColor(ContextCompat.getColor(this, R.color.status_error));
                });
            }
        });
    }

    private void login(TextInputEditText usernameView, TextInputEditText passwordView) {
        String username = textOf(usernameView);
        String password = textOf(passwordView);
        if (username.isEmpty() || password.isEmpty()) {
            toast("请输入用户名和密码");
            return;
        }
        runRequest(() -> api.post("auth/login", new JSONObject()
                .put("username", username).put("password", password)), response -> {
            session.save(response.getJSONObject("data"));
            toast("登录成功");
            showShell();
        }, true);
    }

    private void showRegisterDialog() {
        LinearLayout form = dialogForm();
        TextInputLayout username = input(form, "用户名（3-50 个字符）", InputType.TYPE_CLASS_TEXT);
        TextInputLayout password = input(form, "密码（至少 6 位）", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextInputLayout realName = input(form, "姓名", InputType.TYPE_CLASS_TEXT);
        TextInputLayout phone = input(form, "手机号（选填）", InputType.TYPE_CLASS_PHONE);
        TextInputLayout email = input(form, "邮箱（选填）", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("注册新账号").setView(wrapDialog(form)).setNegativeButton("取消", null)
                .setPositiveButton("注册并登录", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String u = UiFactory.value(username), p = UiFactory.value(password), n = UiFactory.value(realName);
            if (u.length() < 3 || p.length() < 6 || n.isEmpty()) {
                toast("请填写姓名，用户名至少 3 位，密码至少 6 位");
                return;
            }
            runRequest(() -> api.post("auth/register", new JSONObject()
                    .put("username", u).put("password", p).put("realName", n)
                    .put("phone", UiFactory.value(phone)).put("email", UiFactory.value(email))), response -> {
                session.save(response.getJSONObject("data"));
                dialog.dismiss();
                toast("注册成功");
                showShell();
            }, true);
        }));
        dialog.show();
    }

    private void showShell() {
        appBar.setVisibility(View.VISIBLE);
        bottomNavigation.setVisibility(View.VISIBLE);
        currentPage = R.id.nav_home;
        bottomNavigation.setSelectedItemId(R.id.nav_home);
        loadCurrentPage();
    }

    private void loadCurrentPage() {
        if (!session.isLoggedIn()) {
            showLogin();
            return;
        }
        if (currentPage == R.id.nav_products) loadProducts();
        else if (currentPage == R.id.nav_orders) loadOrders();
        else if (currentPage == R.id.nav_feedback) loadFeedback();
        else if (currentPage == R.id.nav_profile) loadProfile();
        else loadHome();
    }

    private void loadHome() {
        setHeader("服务首页", "设备、工单与反馈概览");
        runRequest(() -> {
            JSONObject result = new JSONObject();
            result.put("products", api.get("user-products/mine").getJSONArray("data"));
            result.put("orders", records(api.get("orders/mine?page=1&size=100")));
            result.put("feedback", records(api.get("feedback/mine?page=1&size=100")));
            return result;
        }, data -> renderHome(data.getJSONArray("products"), data.getJSONArray("orders"), data.getJSONArray("feedback")), true);
    }

    private void renderHome(JSONArray products, JSONArray orders, JSONArray feedback) {
        LinearLayout list = pageList();
        String name = session.realName().isEmpty() ? session.username() : session.realName();
        list.addView(UiFactory.text(this, "你好，" + name, 24, R.color.text_primary, true));
        list.addView(UiFactory.text(this, "售后服务进度一目了然", 14, R.color.text_secondary, false));
        list.addView(UiFactory.space(this, 20));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("已绑定产品", products.length(), R.color.brand_primary), weighted());
        stats.addView(statCard("进行中工单", countActiveOrders(orders), R.color.status_warning), weightedWithLeftMargin());
        list.addView(stats);
        LinearLayout stats2 = new LinearLayout(this);
        stats2.setOrientation(LinearLayout.HORIZONTAL);
        stats2.addView(statCard("全部工单", orders.length(), R.color.brand_secondary), weighted());
        stats2.addView(statCard("服务反馈", feedback.length(), R.color.text_secondary), weightedWithLeftMargin());
        list.addView(stats2);

        list.addView(sectionTitle("最近工单"));
        if (orders.length() == 0) list.addView(emptyView("暂无工单，可在“工单”页面提交售后申请"));
        for (int i = 0; i < Math.min(3, orders.length()); i++) list.addView(orderCard(orders.optJSONObject(i), false));
        setPage(list);
    }

    private void loadProducts() {
        setHeader("我的产品", "绑定设备并查看保修状态");
        runRequest(() -> api.get("user-products/mine").getJSONArray("data"), this::renderProducts, true);
    }

    private void renderProducts(JSONArray products) {
        LinearLayout list = pageList();
        MaterialButton bind = UiFactory.button(this, "绑定新产品", v -> showBindProductDialog());
        list.addView(bind, match());
        list.addView(UiFactory.space(this, 12));
        if (products.length() == 0) list.addView(emptyView("暂未绑定产品，请使用设备 SN 码进行绑定"));
        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            MaterialCardView card = UiFactory.card(this);
            LinearLayout box = cardBody();
            box.addView(UiFactory.text(this, join(product.optString("brand"), product.optString("modelName")), 18, R.color.text_primary, true));
            box.addView(meta("SN 码", product.optString("snCode")));
            box.addView(meta("型号", product.optString("modelCode")));
            box.addView(meta("购买日期", valueOrDash(product.optString("purchaseDate"))));
            box.addView(meta("保修期", valueOrDash(product.optString("warrantyStart")) + " 至 " + valueOrDash(product.optString("warrantyEnd"))));
            box.addView(statusText("保修状态：" + warrantyLabel(product.optString("warrantyStatus")),
                    "IN_WARRANTY".equals(product.optString("warrantyStatus")) ? R.color.status_success : R.color.status_warning));
            MaterialButton unbind = UiFactory.button(this, "解除绑定", v -> confirmUnbind(product));
            unbind.setTextColor(ContextCompat.getColor(this, R.color.status_error));
            box.addView(unbind, match());
            card.addView(box);
            list.addView(card);
        }
        setPage(list);
    }

    private void showBindProductDialog() {
        LinearLayout form = dialogForm();
        TextInputLayout sn = input(form, "设备 SN 码", InputType.TYPE_CLASS_TEXT);
        TextInputLayout date = input(form, "购买日期（YYYY-MM-DD，可选）", InputType.TYPE_CLASS_DATETIME);
        TextInputLayout channel = input(form, "购买渠道（可选）", InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("绑定产品")
                .setView(wrapDialog(form)).setNegativeButton("取消", null).setPositiveButton("绑定", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (UiFactory.value(sn).isEmpty()) { toast("请输入 SN 码"); return; }
            String purchaseDate = UiFactory.value(date);
            runRequest(() -> api.post("user-products/bind", new JSONObject()
                    .put("snCode", UiFactory.value(sn))
                    .put("purchaseDate", purchaseDate.isEmpty() ? JSONObject.NULL : purchaseDate)
                    .put("purchaseChannel", nullable(UiFactory.value(channel)))
                    .put("invoiceUrl", JSONObject.NULL)), response -> {
                dialog.dismiss(); toast("产品绑定成功"); loadProducts();
            }, true);
        }));
        dialog.show();
    }

    private void confirmUnbind(JSONObject product) {
        new AlertDialog.Builder(this).setTitle("解除绑定")
                .setMessage("确定解除产品 " + product.optString("snCode") + " 的绑定吗？")
                .setNegativeButton("取消", null).setPositiveButton("解除", (d, w) ->
                        runRequest(() -> api.patch("user-products/" + product.optLong("id") + "/unbind", new JSONObject()),
                                response -> { toast("已解除绑定"); loadProducts(); }, true)).show();
    }

    private void loadOrders() {
        setHeader("售后工单", "提交申请并跟踪处理进度");
        runRequest(() -> records(api.get("orders/mine?page=1&size=100")), this::renderOrders, true);
    }

    private void renderOrders(JSONArray orders) {
        LinearLayout list = pageList();
        list.addView(UiFactory.button(this, "提交售后申请", v -> prepareCreateOrder()), match());
        list.addView(UiFactory.space(this, 12));
        if (orders.length() == 0) list.addView(emptyView("暂无售后工单"));
        for (int i = 0; i < orders.length(); i++) list.addView(orderCard(orders.optJSONObject(i), true));
        setPage(list);
    }

    private MaterialCardView orderCard(JSONObject order, boolean actions) {
        MaterialCardView card = UiFactory.card(this);
        LinearLayout box = cardBody();
        box.addView(UiFactory.text(this, order.optString("orderNo", "售后工单"), 17, R.color.text_primary, true));
        box.addView(statusText(statusLabel(order.optString("status")), statusColor(order.optString("status"))));
        box.addView(meta("产品", join(order.optString("brand"), order.optString("modelName"))));
        box.addView(meta("SN 码", order.optString("snCode")));
        box.addView(meta("服务网点", valueOrDash(order.optString("serviceCenterName"))));
        box.addView(meta("提交时间", valueOrDash(order.optString("submittedAt"))));
        if (actions) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(UiFactory.button(this, "查看详情", v -> loadOrderDetail(order.optLong("id"))), weighted());
            String action = orderActionLabel(order.optString("status"));
            if (action != null) row.addView(UiFactory.button(this, action, v -> handleOrderAction(order)), weightedWithLeftMargin());
            box.addView(row);
            if (canCancel(order.optString("status"))) {
                MaterialButton cancel = UiFactory.button(this, "取消工单", v -> confirmCancelOrder(order));
                cancel.setTextColor(ContextCompat.getColor(this, R.color.status_error));
                box.addView(cancel, match());
            }
        }
        card.addView(box);
        return card;
    }

    private void prepareCreateOrder() {
        runRequest(() -> {
            JSONObject options = new JSONObject();
            options.put("products", api.get("user-products/mine").getJSONArray("data"));
            options.put("centers", api.get("service-centers/options").getJSONArray("data"));
            return options;
        }, data -> showCreateOrderDialog(data.getJSONArray("products"), data.getJSONArray("centers")), true);
    }

    private void showCreateOrderDialog(JSONArray products, JSONArray centers) {
        if (products.length() == 0) { toast("请先绑定产品"); return; }
        if (centers.length() == 0) { toast("当前没有可用服务网点"); return; }
        LinearLayout form = dialogForm();
        Spinner product = spinner(form, labels(products, "brand", "modelName", "snCode"));
        Spinner center = spinner(form, labels(centers, "centerName", "city", "address"));
        Spinner method = spinner(form, new String[]{"寄修", "送修", "上门"});
        TextInputLayout fault = input(form, "故障描述", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextInputLayout name = input(form, "联系人", InputType.TYPE_CLASS_TEXT);
        TextInputLayout phone = input(form, "联系电话", InputType.TYPE_CLASS_PHONE);
        TextInputLayout address = input(form, "联系地址（送修可留空）", InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("提交售后申请")
                .setView(wrapDialog(form)).setNegativeButton("取消", null).setPositiveButton("提交", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String faultText = UiFactory.value(fault), nameText = UiFactory.value(name), phoneText = UiFactory.value(phone);
            String methodCode = new String[]{"MAIL", "DROP_OFF", "ON_SITE"}[method.getSelectedItemPosition()];
            if (faultText.isEmpty() || nameText.isEmpty() || phoneText.isEmpty()) { toast("请完整填写必填信息"); return; }
            if (!"DROP_OFF".equals(methodCode) && UiFactory.value(address).isEmpty()) { toast("寄修或上门必须填写联系地址"); return; }
            runRequest(() -> api.post("orders", new JSONObject()
                    .put("userProductId", products.getJSONObject(product.getSelectedItemPosition()).getLong("id"))
                    .put("serviceCenterId", centers.getJSONObject(center.getSelectedItemPosition()).getLong("id"))
                    .put("serviceMethod", methodCode).put("faultDescription", faultText)
                    .put("contactName", nameText).put("contactPhone", phoneText)
                    .put("contactAddress", nullable(UiFactory.value(address)))), response -> {
                dialog.dismiss(); toast("售后申请已提交"); loadOrders();
            }, true);
        }));
        dialog.show();
    }

    private void loadOrderDetail(long id) {
        runRequest(() -> api.get("orders/" + id).getJSONObject("data"), this::showOrderDetail, true);
    }

    private void showOrderDetail(JSONObject order) {
        LinearLayout box = dialogForm();
        box.addView(UiFactory.text(this, order.optString("orderNo"), 20, R.color.text_primary, true));
        box.addView(statusText(statusLabel(order.optString("status")), statusColor(order.optString("status"))));
        box.addView(meta("产品", join(order.optString("brand"), order.optString("modelName"))));
        box.addView(meta("SN 码", order.optString("snCode")));
        box.addView(meta("服务方式", methodLabel(order.optString("serviceMethod"))));
        box.addView(meta("服务网点", order.optString("serviceCenterName")));
        box.addView(meta("保修判定", warrantyLabel(order.optString("warrantyStatus"))));
        box.addView(meta("联系人", join(order.optString("contactName"), order.optString("contactPhone"))));
        box.addView(meta("联系地址", valueOrDash(order.optString("contactAddress"))));
        box.addView(meta("故障描述", order.optString("faultDescription")));
        if (!order.optString("auditRemark").isEmpty()) box.addView(meta("审核意见", order.optString("auditRemark")));
        JSONObject quote = order.optJSONObject("inspectionCase") == null ? null : order.optJSONObject("inspectionCase").optJSONObject("quote");
        if (quote != null) {
            box.addView(sectionTitle("维修报价"));
            box.addView(meta("报价总额", "¥" + quote.optString("totalAmount", "0.00")));
            box.addView(meta("报价状态", valueOrDash(quote.optString("status"))));
        }
        JSONArray logistics = order.optJSONArray("logisticsRecords");
        if (logistics != null && logistics.length() > 0) {
            box.addView(sectionTitle("物流记录"));
            for (int i = 0; i < logistics.length(); i++) {
                JSONObject item = logistics.optJSONObject(i);
                box.addView(meta("物流", item.optString("logisticsCompany") + " / " + item.optString("trackingNo") + " / " + item.optString("status")));
            }
        }
        JSONArray timeline = order.optJSONArray("timeline");
        if (timeline != null && timeline.length() > 0) {
            box.addView(sectionTitle("处理时间线"));
            for (int i = 0; i < timeline.length(); i++) {
                JSONObject item = timeline.optJSONObject(i);
                box.addView(UiFactory.text(this, item.optString("createdAt") + "  " + actionLabel(item.optString("action")) + "\n" + item.optString("detail"), 13, R.color.text_secondary, false));
                box.addView(UiFactory.space(this, 8));
            }
        }
        new AlertDialog.Builder(this).setTitle("工单详情").setView(wrapDialog(box)).setPositiveButton("关闭", null).show();
    }

    private void handleOrderAction(JSONObject order) {
        String status = order.optString("status");
        if ("SUPPLEMENT_REQUIRED".equals(status)) showSupplementDialog(order);
        else if ("PENDING_SEND".equals(status)) showSendLogisticsDialog(order);
        else if ("PENDING_QUOTE_CONFIRM".equals(status)) showQuoteDialog(order);
        else if ("PENDING_PAYMENT".equals(status)) confirmPayment(order);
        else if ("RETURN_IN_TRANSIT".equals(status)) confirmReturnReceipt(order);
    }

    private void showSupplementDialog(JSONObject order) {
        LinearLayout form = dialogForm();
        if (!order.optString("auditRemark").isEmpty()) form.addView(statusText("审核意见：" + order.optString("auditRemark"), R.color.status_warning));
        TextInputLayout fault = input(form, "故障描述", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextInputLayout name = input(form, "联系人", InputType.TYPE_CLASS_TEXT);
        TextInputLayout phone = input(form, "联系电话", InputType.TYPE_CLASS_PHONE);
        TextInputLayout address = input(form, "联系地址", InputType.TYPE_CLASS_TEXT);
        setValue(fault, order.optString("faultDescription")); setValue(name, order.optString("contactName"));
        setValue(phone, order.optString("contactPhone")); setValue(address, order.optString("contactAddress"));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("补充工单资料").setView(wrapDialog(form))
                .setNegativeButton("取消", null).setPositiveButton("重新提交", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (UiFactory.value(fault).isEmpty() || UiFactory.value(name).isEmpty() || UiFactory.value(phone).isEmpty()) { toast("请完整填写必填信息"); return; }
            runRequest(() -> api.patch("orders/" + order.optLong("id") + "/resubmit", new JSONObject()
                    .put("faultDescription", UiFactory.value(fault)).put("contactName", UiFactory.value(name))
                    .put("contactPhone", UiFactory.value(phone)).put("contactAddress", nullable(UiFactory.value(address)))), response -> {
                dialog.dismiss(); toast("资料已重新提交"); loadOrders();
            }, true);
        }));
        dialog.show();
    }

    private void showSendLogisticsDialog(JSONObject order) {
        LinearLayout form = dialogForm();
        TextInputLayout company = input(form, "物流公司", InputType.TYPE_CLASS_TEXT);
        TextInputLayout tracking = input(form, "物流单号", InputType.TYPE_CLASS_TEXT);
        TextInputLayout name = input(form, "寄件人", InputType.TYPE_CLASS_TEXT);
        TextInputLayout phone = input(form, "寄件人电话", InputType.TYPE_CLASS_PHONE);
        TextInputLayout address = input(form, "寄件地址", InputType.TYPE_CLASS_TEXT);
        TextInputLayout remark = input(form, "物流备注（选填）", InputType.TYPE_CLASS_TEXT);
        setValue(name, order.optString("contactName")); setValue(phone, order.optString("contactPhone")); setValue(address, order.optString("contactAddress"));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("登记寄件物流").setView(wrapDialog(form))
                .setNegativeButton("取消", null).setPositiveButton("确认寄出", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (UiFactory.value(company).isEmpty() || UiFactory.value(tracking).isEmpty() || UiFactory.value(name).isEmpty()
                    || UiFactory.value(phone).isEmpty() || UiFactory.value(address).isEmpty()) { toast("请完整填写寄件信息"); return; }
            runRequest(() -> api.post("orders/" + order.optLong("id") + "/logistics/send", new JSONObject()
                    .put("logisticsCompany", UiFactory.value(company)).put("trackingNo", UiFactory.value(tracking))
                    .put("senderName", UiFactory.value(name)).put("senderPhone", UiFactory.value(phone))
                    .put("senderAddress", UiFactory.value(address)).put("remark", nullable(UiFactory.value(remark)))), response -> {
                dialog.dismiss(); toast("寄件物流已登记"); loadOrders();
            }, true);
        }));
        dialog.show();
    }

    private void showQuoteDialog(JSONObject order) {
        runRequest(() -> api.get("orders/" + order.optLong("id")).getJSONObject("data"), detail -> {
            JSONObject inspection = detail.optJSONObject("inspectionCase");
            JSONObject quote = inspection == null ? null : inspection.optJSONObject("quote");
            if (quote == null) { toast("未找到报价信息"); return; }
            LinearLayout box = dialogForm();
            box.addView(UiFactory.text(this, "应付金额：¥" + quote.optString("totalAmount", "0.00"), 22, R.color.status_warning, true));
            JSONArray items = quote.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                box.addView(meta(item.optString("itemName"), "¥" + item.optString("amount", "0.00")));
            }
            TextInputLayout reason = input(box, "拒绝原因（拒绝时必填）", InputType.TYPE_CLASS_TEXT);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("确认维修报价").setView(wrapDialog(box))
                    .setNegativeButton("暂不处理", null).setNeutralButton("拒绝报价", null).setPositiveButton("确认报价", null).create();
            dialog.setOnShowListener(x -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submitQuoteDecision(order, "CONFIRM", "", dialog));
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(ContextCompat.getColor(this, R.color.status_error));
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    if (UiFactory.value(reason).isEmpty()) { toast("请填写拒绝原因"); return; }
                    submitQuoteDecision(order, "REJECT", UiFactory.value(reason), dialog);
                });
            });
            dialog.show();
        }, true);
    }

    private void submitQuoteDecision(JSONObject order, String decision, String reason, AlertDialog dialog) {
        runRequest(() -> api.patch("orders/" + order.optLong("id") + "/quote-decision", new JSONObject()
                .put("decision", decision).put("reason", reason)), response -> {
            dialog.dismiss(); toast("CONFIRM".equals(decision) ? "报价已确认" : "报价已拒绝"); loadOrders();
        }, true);
    }

    private void confirmPayment(JSONObject order) {
        new AlertDialog.Builder(this).setTitle("模拟支付").setMessage("确认支付该工单的维修费用吗？")
                .setNegativeButton("取消", null).setPositiveButton("确认支付", (d, w) ->
                        runRequest(() -> api.post("orders/" + order.optLong("id") + "/payments/mock",
                                new JSONObject().put("idempotencyKey", "android-" + order.optLong("id") + "-" + UUID.randomUUID())),
                                response -> { toast("支付成功"); loadOrders(); }, true)).show();
    }

    private void confirmReturnReceipt(JSONObject order) {
        runRequest(() -> api.get("orders/" + order.optLong("id")).getJSONObject("data"), detail -> {
            JSONArray logs = detail.optJSONArray("logisticsRecords");
            JSONObject target = null;
            if (logs != null) for (int i = 0; i < logs.length(); i++) {
                JSONObject item = logs.optJSONObject(i);
                if ("RETURN".equals(item.optString("direction")) && "SHIPPED".equals(item.optString("status"))) target = item;
            }
            if (target == null) { toast("未找到运输中的返件记录"); return; }
            long logisticsId = target.optLong("id");
            String tracking = target.optString("trackingNo");
            new AlertDialog.Builder(this).setTitle("确认签收").setMessage("确认已收到返件物流 " + tracking + " 吗？")
                    .setNegativeButton("取消", null).setPositiveButton("确认签收", (d, w) ->
                            runRequest(() -> api.patch("orders/" + order.optLong("id") + "/logistics/" + logisticsId + "/return-receive", new JSONObject()),
                                    response -> { toast("签收成功，工单已完成"); loadOrders(); }, true)).show();
        }, true);
    }

    private void confirmCancelOrder(JSONObject order) {
        new AlertDialog.Builder(this).setTitle("取消工单").setMessage("确定取消工单 " + order.optString("orderNo") + " 吗？")
                .setNegativeButton("返回", null).setPositiveButton("确定取消", (d, w) ->
                        runRequest(() -> api.patch("orders/" + order.optLong("id") + "/cancel", new JSONObject()),
                                response -> { toast("工单已取消"); loadOrders(); }, true)).show();
    }

    private void loadFeedback() {
        setHeader("服务反馈", "评价、投诉与返修申请");
        runRequest(() -> records(api.get("feedback/mine?page=1&size=100")), this::renderFeedback, true);
    }

    private void renderFeedback(JSONArray feedback) {
        LinearLayout list = pageList();
        list.addView(UiFactory.button(this, "提交服务反馈", v -> prepareFeedback()), match());
        list.addView(UiFactory.space(this, 12));
        if (feedback.length() == 0) list.addView(emptyView("暂无服务反馈"));
        for (int i = 0; i < feedback.length(); i++) {
            JSONObject item = feedback.optJSONObject(i);
            MaterialCardView card = UiFactory.card(this);
            LinearLayout box = cardBody();
            box.addView(UiFactory.text(this, feedbackTypeLabel(item.optString("feedbackType")) + " · " + item.optString("orderNo"), 17, R.color.text_primary, true));
            if (item.has("rating") && !item.isNull("rating")) box.addView(meta("评分", item.optInt("rating") + " 星"));
            box.addView(meta("内容", item.optString("content")));
            box.addView(meta("状态", feedbackStatusLabel(item.optString("status"))));
            if (!item.optString("replyContent").isEmpty()) box.addView(meta("客服回复", item.optString("replyContent")));
            box.addView(meta("提交时间", valueOrDash(item.optString("createdAt"))));
            card.addView(box); list.addView(card);
        }
        setPage(list);
    }

    private void prepareFeedback() {
        runRequest(() -> records(api.get("orders/mine?page=1&size=100")), orders -> {
            List<JSONObject> completed = new ArrayList<>();
            for (int i = 0; i < orders.length(); i++) if ("COMPLETED".equals(orders.optJSONObject(i).optString("status"))) completed.add(orders.optJSONObject(i));
            if (completed.isEmpty()) { toast("完成的工单才可以提交反馈"); return; }
            showFeedbackDialog(completed);
        }, true);
    }

    private void showFeedbackDialog(List<JSONObject> orders) {
        LinearLayout form = dialogForm();
        Spinner order = spinner(form, labels(orders, "orderNo", "brand", "modelName"));
        Spinner type = spinner(form, new String[]{"服务评价", "服务投诉", "申请返修"});
        Spinner rating = spinner(form, new String[]{"5 星", "4 星", "3 星", "2 星", "1 星"});
        TextInputLayout contentInput = input(form, "反馈内容", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView hint = UiFactory.text(this, "评价请选择星级；投诉与返修将忽略星级。", 12, R.color.text_secondary, false);
        form.addView(hint);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("提交服务反馈").setView(wrapDialog(form))
                .setNegativeButton("取消", null).setPositiveButton("提交", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String body = UiFactory.value(contentInput);
            if (body.isEmpty()) { toast("请输入反馈内容"); return; }
            int pos = type.getSelectedItemPosition();
            String path = pos == 0 ? "feedback/evaluations" : pos == 1 ? "feedback/complaints" : "feedback/rework";
            try {
                JSONObject request = new JSONObject().put("orderId", orders.get(order.getSelectedItemPosition()).getLong("id")).put("content", body);
                if (pos == 0) request.put("rating", 5 - rating.getSelectedItemPosition());
                runRequest(() -> api.post(path, request), response -> { dialog.dismiss(); toast("反馈提交成功"); loadFeedback(); }, true);
            } catch (Exception error) { toast("反馈数据有误"); }
        }));
        dialog.show();
    }

    private void loadProfile() {
        setHeader("个人中心", "账号信息与服务连接");
        runRequest(() -> api.get("auth/me").getJSONObject("data"), this::renderProfile, true);
    }

    private void renderProfile(JSONObject user) {
        LinearLayout list = pageList();
        MaterialCardView card = UiFactory.card(this);
        LinearLayout box = cardBody();
        box.addView(UiFactory.text(this, user.optString("realName", user.optString("username")), 24, R.color.text_primary, true));
        box.addView(meta("用户名", user.optString("username")));
        box.addView(meta("手机号", valueOrDash(user.optString("phone"))));
        box.addView(meta("邮箱", valueOrDash(user.optString("email"))));
        box.addView(meta("角色", roleLabels(user.optJSONArray("roles"))));
        card.addView(box); list.addView(card);

        MaterialCardView server = UiFactory.card(this);
        LinearLayout serverBox = cardBody();
        serverBox.addView(UiFactory.text(this, "服务器连接", 17, R.color.text_primary, true));
        serverBox.addView(meta("API 地址", ApiClient.BASE_URL));
        serverBox.addView(statusText("已通过公网连接腾讯云服务", R.color.status_success));
        server.addView(serverBox); list.addView(server);

        MaterialButton logout = UiFactory.button(this, "退出登录", v -> logout());
        logout.setTextColor(ContextCompat.getColor(this, R.color.status_error));
        list.addView(logout, match());
        setPage(list);
    }

    private void logout() {
        new AlertDialog.Builder(this).setTitle("退出登录").setMessage("确定退出当前账号吗？")
                .setNegativeButton("取消", null).setPositiveButton("退出", (d, w) ->
                        runRequest(() -> api.post("auth/logout", new JSONObject()), response -> {
                            session.clear(); showLogin();
                        }, true)).show();
    }

    private <T> void runRequest(Task<T> task, Result<T> result, boolean showLoading) {
        if (showLoading) setLoading(true);
        executor.execute(() -> {
            try {
                T value = task.run();
                runOnUiThread(() -> {
                    if (showLoading) setLoading(false);
                    try { result.accept(value); } catch (Exception error) { showError(error); }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (showLoading) setLoading(false);
                    if (error instanceof ApiClient.ApiException && ((ApiClient.ApiException) error).status == 401) {
                        session.clear(); toast("登录已失效，请重新登录"); showLogin();
                    } else showError(error);
                });
            }
        });
    }

    private void setLoading(boolean loading) { loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE); }
    private void showError(Exception error) { toast(error.getMessage() == null ? "请求失败，请稍后重试" : error.getMessage()); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void setHeader(String title, String subtitle) { pageTitle.setText(title); pageSubtitle.setText(subtitle); }

    private LinearLayout pageList() {
        LinearLayout list = UiFactory.vertical(this);
        int p = UiFactory.dp(this, 16);
        list.setPadding(p, p, p, p);
        return list;
    }

    private void setPage(View page) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.removeAllViews(); content.addView(scroll);
    }

    private LinearLayout cardBody() {
        LinearLayout box = UiFactory.vertical(this);
        int p = UiFactory.dp(this, 16);
        box.setPadding(p, p, p, p);
        return box;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = UiFactory.vertical(this);
        int p = UiFactory.dp(this, 4);
        form.setPadding(p, p, p, p);
        return form;
    }

    private ScrollView wrapDialog(View child) {
        ScrollView scroll = new ScrollView(this);
        int p = UiFactory.dp(this, 20);
        scroll.setPadding(p, UiFactory.dp(this, 8), p, 0);
        scroll.addView(child);
        return scroll;
    }

    private TextInputLayout input(LinearLayout parent, String hint, int inputType) {
        TextInputLayout input = UiFactory.input(this, hint, inputType);
        LinearLayout.LayoutParams params = match(); params.bottomMargin = UiFactory.dp(this, 10);
        parent.addView(input, params);
        return input;
    }

    private Spinner spinner(LinearLayout parent, String[] values) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        LinearLayout.LayoutParams params = match(); params.height = UiFactory.dp(this, 52); params.bottomMargin = UiFactory.dp(this, 10);
        parent.addView(spinner, params);
        return spinner;
    }

    private TextView sectionTitle(String text) {
        TextView view = UiFactory.text(this, text, 18, R.color.text_primary, true);
        view.setPadding(0, UiFactory.dp(this, 12), 0, UiFactory.dp(this, 10));
        return view;
    }

    private TextView meta(String label, String value) {
        TextView view = UiFactory.text(this, label + "：" + valueOrDash(value), 14, R.color.text_secondary, false);
        view.setPadding(0, UiFactory.dp(this, 4), 0, UiFactory.dp(this, 4));
        view.setTextIsSelectable(true);
        return view;
    }

    private TextView statusText(String text, int color) {
        TextView view = UiFactory.text(this, text, 14, color, true);
        view.setPadding(0, UiFactory.dp(this, 7), 0, UiFactory.dp(this, 7));
        return view;
    }

    private View emptyView(String message) {
        TextView view = UiFactory.text(this, message, 15, R.color.text_secondary, false);
        view.setGravity(android.view.Gravity.CENTER);
        view.setPadding(UiFactory.dp(this, 20), UiFactory.dp(this, 52), UiFactory.dp(this, 20), UiFactory.dp(this, 52));
        return view;
    }

    private MaterialCardView statCard(String label, int value, int color) {
        MaterialCardView card = UiFactory.card(this);
        LinearLayout box = cardBody();
        box.addView(UiFactory.text(this, String.valueOf(value), 28, color, true));
        box.addView(UiFactory.text(this, label, 13, R.color.text_secondary, false));
        card.addView(box);
        return card;
    }

    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }
    private LinearLayout.LayoutParams weightedWithLeftMargin() { LinearLayout.LayoutParams p = weighted(); p.leftMargin = UiFactory.dp(this, 8); return p; }

    private static JSONArray records(JSONObject response) { return response.optJSONObject("data") == null ? new JSONArray() : response.optJSONObject("data").optJSONArray("records"); }
    private static Object nullable(String text) { return text == null || text.trim().isEmpty() ? JSONObject.NULL : text.trim(); }
    private static String textOf(TextView view) { return view.getText() == null ? "" : view.getText().toString().trim(); }
    private static void setValue(TextInputLayout input, String value) { if (input.getEditText() != null) input.getEditText().setText(value); }
    private static String valueOrDash(String value) { return value == null || value.isEmpty() || "null".equals(value) ? "-" : value; }
    private static String join(String... values) { StringBuilder out = new StringBuilder(); for (String value : values) if (value != null && !value.isEmpty() && !"null".equals(value)) { if (out.length() > 0) out.append(" "); out.append(value); } return out.length() == 0 ? "-" : out.toString(); }

    private static String[] labels(JSONArray data, String... keys) {
        String[] labels = new String[data.length()];
        for (int i = 0; i < data.length(); i++) { JSONObject item = data.optJSONObject(i); String[] values = new String[keys.length]; for (int j = 0; j < keys.length; j++) values[j] = item.optString(keys[j]); labels[i] = join(values); }
        return labels;
    }

    private static String[] labels(List<JSONObject> data, String... keys) {
        String[] labels = new String[data.size()];
        for (int i = 0; i < data.size(); i++) { String[] values = new String[keys.length]; for (int j = 0; j < keys.length; j++) values[j] = data.get(i).optString(keys[j]); labels[i] = join(values); }
        return labels;
    }

    private int countActiveOrders(JSONArray orders) { int count = 0; for (int i = 0; i < orders.length(); i++) { String status = orders.optJSONObject(i).optString("status"); if (!"COMPLETED".equals(status) && !"CANCELLED".equals(status) && !"REJECTED".equals(status)) count++; } return count; }
    private static boolean canCancel(String status) { return "PENDING_REVIEW".equals(status) || "SUPPLEMENT_REQUIRED".equals(status); }

    private static String orderActionLabel(String status) {
        switch (status) {
            case "SUPPLEMENT_REQUIRED": return "补充资料";
            case "PENDING_SEND": return "登记寄件";
            case "PENDING_QUOTE_CONFIRM": return "处理报价";
            case "PENDING_PAYMENT": return "立即支付";
            case "RETURN_IN_TRANSIT": return "确认签收";
            default: return null;
        }
    }

    private static String statusLabel(String status) {
        switch (status) {
            case "PENDING_REVIEW": return "待审核"; case "SUPPLEMENT_REQUIRED": return "待补充资料";
            case "REJECTED": return "已驳回"; case "PENDING_SEND": return "待寄送"; case "IN_TRANSIT": return "运输中";
            case "PENDING_INSPECTION": return "待检测"; case "PENDING_QUOTE_CONFIRM": return "待确认报价";
            case "PENDING_PAYMENT": return "待支付"; case "PENDING_REPAIR": return "待维修"; case "REPAIRING": return "维修中";
            case "PENDING_QUALITY": return "待质检"; case "QUALITY_CHECKING": return "质检中"; case "QUALITY_FAILED": return "质检未通过";
            case "PENDING_RETURN": return "待返件"; case "RETURN_IN_TRANSIT": return "返件运输中"; case "COMPLETED": return "已完成";
            case "CANCELLED": return "已取消"; default: return valueOrDash(status);
        }
    }

    private int statusColor(String status) {
        if ("COMPLETED".equals(status)) return R.color.status_success;
        if ("REJECTED".equals(status) || "QUALITY_FAILED".equals(status)) return R.color.status_error;
        if ("SUPPLEMENT_REQUIRED".equals(status) || "PENDING_PAYMENT".equals(status) || "PENDING_QUOTE_CONFIRM".equals(status)) return R.color.status_warning;
        return R.color.brand_primary;
    }

    private static String warrantyLabel(String status) {
        if ("IN_WARRANTY".equals(status)) return "保修期内";
        if ("OUT_OF_WARRANTY".equals(status)) return "已过保";
        if ("VOID".equals(status)) return "保修失效";
        return valueOrDash(status);
    }

    private static String methodLabel(String method) {
        if ("MAIL".equals(method)) return "寄修"; if ("DROP_OFF".equals(method)) return "送修"; if ("ON_SITE".equals(method)) return "上门"; return valueOrDash(method);
    }

    private static String feedbackTypeLabel(String type) {
        if ("EVALUATION".equals(type)) return "服务评价"; if ("COMPLAINT".equals(type)) return "服务投诉"; if ("REWORK".equals(type)) return "返修申请"; return valueOrDash(type);
    }

    private static String feedbackStatusLabel(String status) {
        if ("PENDING".equals(status)) return "待处理"; if ("REPLIED".equals(status)) return "已回复"; if ("CLOSED".equals(status)) return "已关闭"; return valueOrDash(status);
    }

    private static String actionLabel(String action) {
        switch (action) {
            case "CREATE": return "提交申请"; case "AUDIT_APPROVED": return "审核通过"; case "AUDIT_SUPPLEMENT": return "要求补充";
            case "AUDIT_REJECTED": return "审核驳回"; case "RESUBMIT": return "重新提交"; case "CANCEL": return "取消工单";
            case "CREATE_LOGISTICS": return "登记寄件"; case "RECEIVE_LOGISTICS": return "网点收件"; case "INSPECTION_COMPLETED": return "检测完成";
            case "QUOTE_CONFIRMED": return "确认报价"; case "QUOTE_REJECTED": return "拒绝报价"; case "PAYMENT_SUCCESS": return "支付成功";
            case "REPAIR_COMPLETED": return "维修完成"; case "QUALITY_COMPLETED": return "质检完成"; case "CREATE_RETURN_LOGISTICS": return "登记返件";
            case "RETURN_RECEIVED": return "确认签收"; default: return valueOrDash(action);
        }
    }

    private static String roleLabels(JSONArray roles) {
        if (roles == null || roles.length() == 0) return "普通用户";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < roles.length(); i++) { if (i > 0) text.append("、"); String role = roles.optString(i); text.append("USER".equals(role) ? "普通用户" : role); }
        return text.toString();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private interface Task<T> { T run() throws Exception; }
    private interface Result<T> { void accept(T value) throws Exception; }
}
