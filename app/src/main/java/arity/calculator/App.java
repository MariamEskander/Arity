package arity.calculator;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

import com.instabug.library.Instabug;
import com.instabug.library.invocation.InstabugInvocationEvent;

public class App extends Application {

  @Override
  public void onCreate() {
    super.onCreate();

    AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES);
    new Instabug.Builder(this, "7852751d6bac7420c21180e542a03b02")
            .setInvocationEvents(InstabugInvocationEvent.FLOATING_BUTTON, InstabugInvocationEvent.SHAKE, InstabugInvocationEvent.SCREENSHOT)
            .build();

  }
}
