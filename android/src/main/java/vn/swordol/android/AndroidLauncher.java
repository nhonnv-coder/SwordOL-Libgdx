package vn.swordol.android;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import vn.swordol.client.SwordGame;

public final class AndroidLauncher extends AndroidApplication {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
    config.useAccelerometer = false;
    config.useCompass = false;
    initialize(new SwordGame(), config);
  }
}
