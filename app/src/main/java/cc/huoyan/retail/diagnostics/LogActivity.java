package cc.huoyan.retail.diagnostics;

import cc.huoyan.retail.home.HomeActivity;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import cc.huoyan.retail.common.ui.RetailAdapter;
import cc.huoyan.retail.common.ui.RetailAdapter.OnItemClickListener;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.BaseActivity;
import cc.huoyan.retail.common.ui.ToastEx;


import java.io.File;
import java.util.List;

public class LogActivity extends BaseActivity implements LogPresenter.View {

    private RecyclerView recyclerView;
    private LogFileAdapter logFileAdapter;
    private RecyclerView pathRecycler;
    private LogPathAdapter logPathAdapter;
    /** 协调目录导航和文件读取，不持有界面控件。 */
    private LogPresenter presenter;

    @Override
    protected int initContentView() {
        return R.layout.activity_log;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View back = findViewById(R.id.navBack);
        if (back == null) back = findViewById(R.id.quit);
        if (back instanceof android.widget.TextView && findViewById(R.id.navBack) != null)
            ((android.widget.TextView) back).setText(R.string.home_back);
        back.setOnClickListener(view -> {
            if (findViewById(R.id.navBack) != null) {
                HomeActivity.open(this);
            }
            finish();
        });
        android.widget.TextView navTitle = findViewById(R.id.navTitle);
        if (navTitle != null) navTitle.setText(R.string.land_title_logs);
        logFileAdapter = new LogFileAdapter();
        logFileAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull RetailAdapter<?> adapter, @NonNull View view, int position) {
                presenter.open(logFileAdapter.getData().get(position));
            }
        });
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(mContext));
        recyclerView.setAdapter(logFileAdapter);
        // 创建分割线（参数：上下文 + 方向，垂直或水平）
        DividerItemDecoration divider = new DividerItemDecoration(
                this,
                DividerItemDecoration.VERTICAL  // 垂直列表用VERTICAL，水平列表用HORIZONTAL
        );
        // 可选：自定义分割线样式（需在res/drawable下定义divider.xml）
        divider.setDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.divider, null));
        if (findViewById(R.id.logFileContent) == null) recyclerView.addItemDecoration(divider);

        logPathAdapter = new LogPathAdapter();
        logPathAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull RetailAdapter<?> adapter, @NonNull View view, int position) {
                presenter.breadcrumb(position);
            }
        });
        pathRecycler = findViewById(R.id.pathRecycler);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        pathRecycler.setLayoutManager(linearLayoutManager);
        pathRecycler.setAdapter(logPathAdapter);
        android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        presenter = new LogPresenter(this, new File(Environment.getExternalStorageDirectory(), "retail/log/app"),
                new LogFileSource(getString(R.string.land_log_truncated)),
                cc.huoyan.retail.common.IOThreadHelper.getInstance()::post, ui::post);
    }

    /** 恢复页面时按布局决定是否自动选中最新日志。 */
    @Override protected void onResume() {
        super.onResume();
        presenter.resume(findViewById(R.id.logFileContent) != null);
    }
    /** 展示目录列表和根路径导航。 */
    @Override public void showDirectory(List<LogFIleDirectory> entries, List<String> path) {
        logFileAdapter.setList(entries); logPathAdapter.setList(path);
    }
    /** 标记正在读取的文件行。 */
    @Override public void selectFile(File file) { logFileAdapter.selectFile(file); }
    /** 按当前布局展示内容，并将文本滚动位置复位。 */
    @Override public void showText(File file, String text) {
        if (isFinishing() || isDestroyed()) return;
        android.widget.TextView content = findViewById(R.id.logFileContent);
        if (content == null) new LogDialog(text).show(getSupportFragmentManager(), "logDialog");
        else {
            content.setText(text);
            ((android.widget.TextView) findViewById(R.id.logFileTitle)).setText(file.getName());
            ((View) content.getParent()).scrollTo(0, 0);
        }
    }
    /** 文件读取失败时给出本地化提示。 */
    @Override public void missingFile() {
        if (!isFinishing() && !isDestroyed()) ToastEx.createToast(this, getString(R.string.log_file_missing));
    }
    /** 退出后不再接收文件内容。 */
    @Override protected void onDestroy() { presenter.detach(); super.onDestroy(); }
}
