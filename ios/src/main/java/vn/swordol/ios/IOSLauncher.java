package vn.swordol.ios;

import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration;
import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.uikit.UIApplication;
import vn.swordol.client.SwordGame;

public final class IOSLauncher extends IOSApplication.Delegate {
  @Override
  protected IOSApplication createApplication() {
    return new IOSApplication(new SwordGame(), new IOSApplicationConfiguration());
  }

  public static void main(String[] args) {
    try (NSAutoreleasePool pool = new NSAutoreleasePool()) {
      UIApplication.main(args, null, IOSLauncher.class);
    }
  }
}
