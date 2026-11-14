package br.com.otimizadordecorte2d;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;
import android.content.Context;
import android.widget.Toast;
import android.widget.EditText;
import android.app.AlertDialog;
import android.text.InputType;
import java.io.OutputStream;
import java.io.IOException;

public class MainActivity extends Activity {
    private static final int SAVE_JSON = 1001;
    private static final int OPEN_JSON = 1002;
    private WebView webView;
    private ValueCallback<Uri[]> chooserCallback;
    private String pendingJson;
    private String pendingFilename = "projeto_corte.json";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (chooserCallback != null) chooserCallback.onReceiveValue(null);
                chooserCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivityForResult(intent, OPEN_JSON);
                    return true;
                } catch (Exception e) {
                    chooserCallback = null;
                    Toast.makeText(MainActivity.this, "Não foi possível abrir arquivos.",
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void saveProject(final String filename, final String json) {
            runOnUiThread(() -> {
                pendingFilename = (filename == null || filename.trim().isEmpty())
                        ? "projeto_corte.json" : filename;
                pendingJson = json;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, pendingFilename);
                startActivityForResult(intent, SAVE_JSON);
            });
        }

        @JavascriptInterface
        public void printPage(final String suggestedName) {
            runOnUiThread(() -> {
                final EditText nameInput = new EditText(MainActivity.this);
                nameInput.setSingleLine(true);
                nameInput.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                String initialName = (suggestedName == null) ? "" : suggestedName.trim();
                if (initialName.isEmpty()) initialName = "Plano de corte";
                nameInput.setText(initialName);
                nameInput.setSelection(nameInput.getText().length());

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Nome do arquivo PDF")
                        .setMessage("Digite o nome que deseja usar ao salvar o PDF:")
                        .setView(nameInput)
                        .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                        .setPositiveButton("Continuar", (dialog, which) -> {
                            String fileName = nameInput.getText().toString().trim();
                            if (fileName.isEmpty()) fileName = "Plano de corte";
                            fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
                            if (fileName.toLowerCase().endsWith(".pdf"))
                                fileName = fileName.substring(0, fileName.length() - 4);
                            startPrint(fileName);
                        })
                        .show();
            });
        }

        private void startPrint(String documentName) {
            try {
                PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                PrintDocumentAdapter adapter =
                        webView.createPrintDocumentAdapter(documentName);
                manager.print(documentName, adapter, null);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Falha ao iniciar impressão.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SAVE_JSON) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && pendingJson != null) {
                OutputStream out = null;
                try {
                    out = getContentResolver().openOutputStream(data.getData());
                    if (out == null) throw new IOException("Destino indisponível");
                    out.write(pendingJson.getBytes("UTF-8"));
                    out.flush();
                    Toast.makeText(this, "Projeto salvo.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Erro ao salvar: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                } finally {
                    if (out != null) try { out.close(); } catch (IOException ignored) {}
                }
            }
            pendingJson = null;
        } else if (requestCode == OPEN_JSON && chooserCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                result = new Uri[]{data.getData()};
            chooserCallback.onReceiveValue(result);
            chooserCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
