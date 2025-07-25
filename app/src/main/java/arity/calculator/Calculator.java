// Copyright (C) 2009 Mihai Preda
  
package arity.calculator;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.ListView;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.util.Log;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;

import org.javia.arity.Util;
import org.javia.arity.*;

public class Calculator extends AppCompatActivity implements TextWatcher,
						    View.OnKeyListener, View.OnClickListener,
                            SharedPreferences.OnSharedPreferenceChangeListener
{
    static final char MINUS = '\u2212', TIMES = '\u00d7', DIV = '\u00f7', SQRT = '\u221a', PI = '\u03c0', 
        UP_ARROW = '\u21e7', DN_ARROW = '\u21e9', ARROW = '\u21f3';

    private static final int MSG_INPUT_CHANGED = 1;
    private static final String INFINITY = "Infinity";
    private static final String INFINITY_UNICODE = "\u221e";

    static Symbols symbols = new Symbols();
    static Function function;

    private TextView result;
    private EditText input;
    private ListView historyView;
    private GraphView graphView;
    private Graph3dView graph3dView;
    private History history;
    private HistoryAdapter adapter;   
    private int nDigits = 0;
    private boolean pendingClearResult;
    private boolean isAlphaVisible;
    private KeyboardView alpha, digits;
    static ArrayList<Function> graphedFunction;
    static Defs defs;
    private ArrayList<Function> auxFuncs = new ArrayList<Function>();
    static boolean useSmoothShading3D;
    static int resolution3D;
    private static String savedInputText ="";

    private static final char[][] ALPHA = {
        {'q', 'w', '=', ',', ';', SQRT, '!', '\''},
        {'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'},
        {'a', 's', 'd', 'f', 'g', 'h', 'j', 'k'},
        {'z', 'x', 'c', 'v', 'b', 'n', 'm', 'l'},
    };

    private static final char[][] DIGITS = {
        {'7', '8', '9', '%', '^', ARROW},
        {'4', '5', '6','(', ')', 'C'},
        {'1', '2', '3', TIMES, DIV, 'E'},
        {'0', '.', PI, '+', MINUS, 'E'},
    };

    private static final char[][] DIGITS2 = {
        {'0', '.', '+', MINUS, TIMES, DIV, '^', '(', ')', 'C'},        
        {'1', '2', '3', '4', '5', '6', '7', '8', '9', 'E'},
    };

    /*
    private static final char[][] DIGITS3 = {
        {'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'},
        {'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', PI},
        {'z', 'x', 'c', 'v', 'b', 'n', 'm', ',', '=', '%'},
        {'0', '.', '+', MINUS, TIMES, DIV, '^', '(', ')', 'C'},        
        {'1', '2', '3', '4', '5', '6', '7', '8', '9', 'E'},
    };
    */

    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        internalConfigChange(config);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void internalConfigChange(Configuration config) {
        setContentView(R.layout.main);  
        graphView = (GraphView) findViewById(R.id.graph);
        graph3dView = (Graph3dView) findViewById(R.id.graph3d);
        historyView = (ListView) findViewById(R.id.history);
              
        final boolean isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        // final boolean hasKeyboard = config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
        
        alpha = (KeyboardView) findViewById(R.id.alpha);
        digits = (KeyboardView) findViewById(R.id.digits);
        if (isLandscape) {                        
            digits.init(DIGITS2, false, true);
            isAlphaVisible = false;
        } else {
            alpha.init(ALPHA, false, false);
            digits.init(DIGITS, true, true);
            updateAlpha();
        }

        result = (TextView) findViewById(R.id.result);

        Editable oldText = input != null ? input.getText() : null;
        input  = (EditText) findViewById(R.id.input);
        input.setOnKeyListener(this);
        input.addTextChangedListener(this);
        input.setEditableFactory(new CalculatorEditable.Factory());
        input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);  //With InputType 0 the cursor vanishes
        input.setTextIsSelectable(true);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);  //make sure, soft-keyboard is not opened (if window is focusable, setting this flag prevents this window from becoming the target of the input method)
	    changeInput(savedInputText);
        if (oldText != null) {
            input.setText(oldText);
        }
        input.requestFocus();
        graphView.setOnClickListener(this);
        graph3dView.setOnClickListener(this);
        if (historyView != null) {
            historyView.setAdapter(adapter);
	    historyView.setOnItemClickListener((adapterView, view, i, l) ->{
            history.moveToPos(i);
            changeInput(history.getText()); });

	    historyView.setOnItemLongClickListener((adapterView, view, i, l) -> {
            history.deletePos(i);
            adapter.notifyDataSetInvalidated();
            return true; });
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SplashScreen.installSplashScreen(this);
        if (prefs.getBoolean("dyn_colors",true)) {
            DynamicColors.applyToActivityIfAvailable(this);
            DynamicColors.applyToActivitiesIfAvailable(this.getApplication());
        }
        getWindow().setStatusBarColor(arity.calculator.Util.getThemeColor(this, R.attr.colorPrimaryDark));
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean isDarkMode = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
            WindowInsetsController insetsController = getWindow().getInsetsController();
            if (insetsController != null) {
                if (isDarkMode) {
                    // Dark mode: remove light status bar appearance (use light icons)
                    insetsController.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    // Light mode: enable light status bar appearance (dark icons)
                    insetsController.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            }
        }

        history = new History(this);
        adapter = new HistoryAdapter(this, history);
        internalConfigChange(getResources().getConfiguration());
        
	defs = new Defs(this, symbols);
	if (history.fileNotFound) {
	    String[] init = {
	    "4\u00d7sin(x^2+y^2)\u00f7(1+x^2+y^2)\u00d7cos(x\u00d7y)",
		"sqrt(pi)\u00f70.5!",
		"e^(i\u00d7pi)",
		"ln(e^100)",
                "sin(x)",
                "x^2"
	    };
	    nDigits = 10;
	    for (String s : init) {
		onEnter(s);
	    }
	    nDigits = 0;
	}
        prefs.registerOnSharedPreferenceChangeListener(this);
        String value = prefs.getString("3d_shading", "smooth");
        useSmoothShading3D = value.equals("smooth");
        resolution3D = Integer.parseInt(prefs.getString("3d_resolution","144"));

        if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(this,"https://github.com/woheller69/Arity");
    }
    
    public void onPause() {
        super.onPause();
        graph3dView.onPause();
        savedInputText = input.getText().toString();
        history.save();
        defs.save();
    }

    public void onResume() {
        changeInput(savedInputText);
        super.onResume();
        graph3dView.onResume();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        (new MenuInflater(this)).inflate(R.menu.main, menu);
        return true;
    }
    
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.clear_history).setEnabled(history.size() > 0);
        menu.findItem(R.id.list_defs).setEnabled(defs.size() > 0);
        // menu.findItem(R.id.clear_defs).setEnabled(defs.size() > 0);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        int id = item.getItemId();
        if (id == R.id.list_defs) {
            startActivity(new Intent(this, ListDefs.class));
        } else if (id == R.id.help) {
            startActivity(new Intent(this, Help.class));
        } else if (id == R.id.clear_history) {
            history.clear();
            history.save();
            adapter.notifyDataSetInvalidated();
        } else if (id == R.id.clear_defs) {
            defs.clear();
            defs.save();
        } else if (id == R.id.settings) {
            startActivity(new Intent(this, Settings.class));
        } else if (id == R.id.about) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/woheller69/Arity ")));
        } else {
            return false;
        }
        return true;
    }

    //OnSharedPreferenceChangeListener
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {       
        if (key.equals("3d_shading")) {
            useSmoothShading3D = prefs.getString(key, "smooth").equals("smooth");
            // Calculator.log("useHigh quality changed to " + useHighQuality3d);
        } else if (key.equals("3d_resolution")){
            resolution3D = Integer.parseInt(prefs.getString("3d_resolution","72"));
        }
    }

    //OnClickListener
    public void onClick(View target) {
        if (target == graphView || target == graph3dView) {
            startActivity(new Intent(this, ShowGraph.class));
        }
    }
    
    // TextWatcher
    public void afterTextChanged(Editable s) {
        // handler.removeMessages(MSG_INPUT_CHANGED);
        // handler.sendEmptyMessageDelayed(MSG_INPUT_CHANGED, 250);
        evaluate();
        /*
	if (pendingClearResult && s.length() != 0) {
            if (!(s.length() == 4 && s.toString().startsWith("ans"))) {
                result.setText(null);
            }
            showGraph(null);
	    pendingClearResult = false;
	}
        */
    }
    
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }


    // OnKeyListener
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                doEnter();
                break;
                
            case KeyEvent.KEYCODE_DPAD_UP:
                onUp();
                break;
                
            case KeyEvent.KEYCODE_DPAD_DOWN:            
                onDown();
                break;
            default:
                return false;
            }
            return true;
        } else {
            switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            }
            return false;
        }
    }
    
    /*
    private Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {                    
                case MSG_INPUT_CHANGED:
                    // String text = input.getText().toString();
                    evaluate();
                }
            }
        };
    */

    static void log(String mes) {
        if (true) {
            Log.d("Calculator", mes);
        }
    }

    void evaluate() {
        evaluate(input.getText().toString());
    }
    
    private String formatEval(Complex value) {
	if (nDigits == 0) {
            nDigits = getResultSpace();
        }
	String res = Util.complexToString(value, nDigits, 2);
	return res.replace(INFINITY, INFINITY_UNICODE);
    }

    private void evaluate(String text) {
        // log("evaluate " + text);
        if (text.length() == 0) {
            result.setEnabled(false);
            return;
        }

        auxFuncs.clear();
        int end = -1;
        do {
            text = text.substring(end+1);
            end  = text.indexOf(';');
            String slice = end == -1 ? text : text.substring(0, end);
            try {
                Function f = symbols.compile(slice);
                auxFuncs.add(f);
            } catch (SyntaxException e) {
                continue;
            }
        } while (end != -1);
        
        graphedFunction = auxFuncs;
        int size = auxFuncs.size();
        if (size == 0) {
            result.setEnabled(false);
            return;
        } else if (size == 1) {
            Function f = auxFuncs.get(0);
            int arity = f.arity();
            // Calculator.log("res " + f);
            if (arity == 1 || arity == 2) {
                result.setText(null);
                showGraph(f);
            } else if (arity == 0) {
                result.setText(formatEval(f.evalComplex()));
                result.setEnabled(true);
                showGraph(null);
            } else {
                result.setText(R.string.function);
                result.setEnabled(true);
                showGraph(null);
            }
        } else {
            graphView.setFunctions(auxFuncs);
            if (graphView.getVisibility() != View.VISIBLE) {
                if (isAlphaVisible) {
                    isAlphaVisible = false;
                    updateAlpha();
                }
                result.setVisibility(View.GONE);
                historyView.setVisibility(View.GONE);
                graph3dView.setVisibility(View.GONE);
                graph3dView.onPause();
                graphView.setVisibility(View.VISIBLE);                
            }
        }
    }

    private int getResultSpace() {
        int width = result.getWidth() - result.getTotalPaddingLeft() - result.getTotalPaddingRight();
        float oneDigitWidth = result.getPaint().measureText("5555555555") / 10f;
        return (int) (width / oneDigitWidth);
    }

    private void updateAlpha() {
        alpha.setVisibility(isAlphaVisible ? View.VISIBLE: View.GONE);
        digits.setAboveView(isAlphaVisible ? alpha : null);        
    }

    private StringBuilder oneChar = new StringBuilder(" ");
    void onKey(char key) {
        if (key == 'E') {
            doEnter();
        } else if (key == 'C') {
            runOnUiThread(this::doBackspace);
        }else if (key == ARROW) {
            isAlphaVisible = !isAlphaVisible;
            updateAlpha();
        } else {
            int cursor = input.getSelectionStart();
            oneChar.setCharAt(0, key);
            input.getText().insert(cursor, oneChar);
        }
    }

    private void showGraph(Function f) {
        if (f == null) {
            if (historyView.getVisibility() != View.VISIBLE) {
                graphView.setVisibility(View.GONE);
                graph3dView.setVisibility(View.GONE);
                graph3dView.onPause();
                historyView.setVisibility(View.VISIBLE);
                result.setVisibility(View.VISIBLE);
            }
        } else {
            // graphedFunction = f;
            if (f.arity() == 1) {
                graphView.setFunction(f);
                if (graphView.getVisibility() != View.VISIBLE) {

                    result.setVisibility(View.GONE);
                    historyView.setVisibility(View.GONE);
                    graph3dView.setVisibility(View.GONE);
                    graph3dView.onPause();
                    graphView.setVisibility(View.VISIBLE);
                }
            } else {
                graph3dView.setFunction(f);
                if (graph3dView.getVisibility() != View.VISIBLE) {

                    result.setVisibility(View.GONE);
                    historyView.setVisibility(View.GONE);
                    graphView.setVisibility(View.GONE);
                    graph3dView.setVisibility(View.VISIBLE);
                    graph3dView.onResume();
                }
            }
        }
    }

    void onEnter() {
	onEnter(input.getText().toString());
    }

    void onEnter(String text) {
	boolean historyChanged = false;
	try {
	    FunctionAndName fan = symbols.compileWithName(text);
	    if (fan.name != null) {
		symbols.define(fan);
		defs.add(text);
	    }
	    Function f = fan.function;
            int arity = f.arity();
            Complex value = null;
            if (arity == 0) {
                value = f.evalComplex();
                symbols.define("ans", value);
                if (fan.name != null) symbols.define(fan.name,value);
            }
	    historyChanged = arity == 0 ?
		history.onEnter(text, formatEval(value)) :
		history.onEnter(text, null);
	} catch (SyntaxException e) {
	    historyChanged = history.onEnter(text, null);
	}
        showGraph(null);
        if (historyChanged) {
            adapter.notifyDataSetInvalidated();
        }
        if (text.length() == 0) {
            result.setText(null);
        }
	changeInput(history.getText());
    }
    
    private void changeInput(String newInput) {
        input.setText(newInput);
	input.setSelection(newInput.length());
        /*
	if (newInput.length() > 0) {
	    result.setText(null);
	} else {
	    pendingClearResult = true;
	}
        */
        /*
        if (result.getText().equals("function")) {
            result.setText(null);
        }
        */
    }
    
    /*
    private void updateChecked() {
        int pos = history.getListPos();
        if (pos >= 0) {
            log("check " + pos);
            historyView.setItemChecked(pos, true);
            adapter.notifyDataSetInvalidated();
        }
    }
    */

    void onUp() {
        if (history.moveUp()) {
            changeInput(history.getText());
            // updateChecked();
        }
    }

    void onDown() {
        if (history.moveDown()) {
            changeInput(history.getText());
            // updateChecked();
        }
    }
    
    private static final KeyEvent 
        KEY_DEL = new KeyEvent(0, KeyEvent.KEYCODE_DEL),
        KEY_ENTER = new KeyEvent(0, KeyEvent.KEYCODE_ENTER);

    void doEnter() {
        onEnter();
    }

    void doBackspace() {
        input.dispatchKeyEvent(KEY_DEL);
        if (input.getText().toString().length()==0) {
            showGraph(null);
        }
    }
}
