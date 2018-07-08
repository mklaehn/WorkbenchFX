package com.dlsc.workbenchfx;

import com.dlsc.workbenchfx.module.WorkbenchModule;
import com.dlsc.workbenchfx.view.controls.GlassPane;
import com.dlsc.workbenchfx.view.controls.NavigationDrawer;
import com.dlsc.workbenchfx.view.controls.module.Page;
import com.dlsc.workbenchfx.view.controls.module.Tab;
import com.dlsc.workbenchfx.view.controls.module.Tile;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Skin;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Contains all the model logic for the workbench.
 *
 * @author François Martin
 * @author Marco Sanfratello
 */
public class Workbench extends Control {
  private static final Logger LOGGER =
      LogManager.getLogger(Workbench.class.getName());

  public static final String STYLE_CLASS_ACTIVE_TAB = "active-tab";
  public static final String STYLE_CLASS_ACTIVE_HOME = "active-home";

  // Custom Controls
  private ObjectProperty<NavigationDrawer> navigationDrawer = new SimpleObjectProperty<>();

  // Lists
  private final ObservableSet<Node> toolbarControlsRight =
      FXCollections.observableSet(new LinkedHashSet<>());
  private final ObservableSet<Node> toolbarControlsLeft =
      FXCollections.observableSet(new LinkedHashSet<>());
  private final ObservableList<MenuItem> navigationDrawerItems =
      FXCollections.observableArrayList();

  /**
   * Map containing all overlays which have been loaded into the scene graph, with their
   * corresponding {@link GlassPane}.
   */
  private final ObservableMap<Node, GlassPane> overlays = FXCollections.observableHashMap();
  private final ObservableSet<Node> nonBlockingOverlaysShown = FXCollections.observableSet();
  private final ObservableSet<Node> blockingOverlaysShown = FXCollections.observableSet();

  // Modules
  /**
   * List of all modules.
   */
  private final ObservableList<WorkbenchModule> modules = FXCollections.observableArrayList();

  /**
   * List of all currently open modules. Open modules are being displayed as open tabs in the
   * application.
   */
  private final ObservableList<WorkbenchModule> openModules = FXCollections.observableArrayList();

  /**
   * Currently active module. Active module is the module, which is currently being displayed in the
   * view. When the home screen is being displayed, {@code activeModule} and {@code
   * activeModuleView} are null.
   */
  private final ObjectProperty<WorkbenchModule> activeModule = new SimpleObjectProperty<>();
  private final ObjectProperty<Node> activeModuleView = new SimpleObjectProperty<>();

  // Factories
  /**
   * The factories which are called when creating Tabs, Tiles and Pages of Tiles for the Views. They
   * require a module whose attributes are used to create the Nodes.
   */
  private final ObjectProperty<Callback<Workbench, Tab>> tabFactory =
      new SimpleObjectProperty<>(this, "tabFactory");
  private final ObjectProperty<Callback<Workbench, Tile>> tileFactory =
      new SimpleObjectProperty<>(this, "tileFactory");
  private final ObjectProperty<Callback<Workbench, Page>> pageFactory =
      new SimpleObjectProperty<>(this, "pageFactory");

  // Properties
  private final IntegerProperty modulesPerPage;
  private final IntegerProperty amountOfPages;

  Workbench(WorkbenchBuilder builder) {
    modulesPerPage = new SimpleIntegerProperty(builder.modulesPerPage);
    amountOfPages = new SimpleIntegerProperty(calculateAmountOfPages());
    initAmountOfPagesBinding();
    tabFactory.set(builder.tabFactory);
    tileFactory.set(builder.tileFactory);
    pageFactory.set(builder.pageFactory);
    initToolbarControls(builder);
    initNavigationDrawer(builder);
    initModules(builder.modules);
    setupCleanup();
  }

  private void initAmountOfPagesBinding() {
    amountOfPages.bind(
        Bindings.createIntegerBinding(this::calculateAmountOfPages,
            modulesPerPageProperty(), getModules()
        )
    );
  }

  @Override
  protected Skin<?> createDefaultSkin() {
    return new WorkbenchSkin(this);
  }

  /**
   * Creates a builder for {@link Workbench}.
   *
   * @param modules which should be loaded for the application
   * @return builder object
   */
  public static WorkbenchBuilder builder(WorkbenchModule... modules) {
    return new WorkbenchBuilder(modules);
  }

  private void initToolbarControls(WorkbenchBuilder builder) {
    if (builder.toolbarControlsLeft != null) {
      toolbarControlsLeft.addAll(List.of(builder.toolbarControlsLeft));
    }

    if (builder.toolbarControlsRight != null) {
      toolbarControlsRight.addAll(List.of(builder.toolbarControlsRight));
    }
  }

  private void initNavigationDrawer(WorkbenchBuilder builder) {
    if (builder.navigationDrawerItems != null) {
      navigationDrawerItems.addAll(builder.navigationDrawerItems);
    }
    // when control of navigation drawer changes, pass in the workbench object
    navigationDrawerProperty().addListener((observable, oldControl, newControl) -> {
      if (oldControl != newControl) {
        newControl.setWorkbench(this);
      }
    });
    setNavigationDrawer(builder.navigationDrawer);
  }

  private void initModules(WorkbenchModule... modules) {
    this.modules.addAll(modules);

    // handle changes of the active module
    activeModule.addListener((observable, oldModule, newModule) -> {
      LOGGER.trace("Module Listener - Old Module: " + oldModule);
      LOGGER.trace("Module Listener - New Module: " + newModule);
      if (oldModule != newModule) {
        boolean fromHomeScreen = oldModule == null;
        LOGGER.trace("Active Module Listener - Previous view home screen: " + fromHomeScreen);
        boolean fromDestroyed = !openModules.contains(oldModule);
        LOGGER.trace("Active Module Listener - Previous module destroyed: " + fromDestroyed);
        if (!fromHomeScreen && !fromDestroyed) {
          // switch from one module to another
          LOGGER.trace("Active Module Listener - Deactivating old module - " + oldModule);
          oldModule.deactivate();
        }
        boolean toHomeScreen = newModule == null;
        if (toHomeScreen) {
          // switch to home screen
          LOGGER.trace("Active Module Listener - Switched to home screen");
          activeModuleView.setValue(null);
          return;
        }
        if (!openModules.contains(newModule)) {
          // module has not been loaded yet
          LOGGER.trace("Active Module Listener - Initializing module - " + newModule);
          newModule.init(this);
          openModules.add(newModule);
        }
        LOGGER.trace("Active Module Listener - Activating module - " + newModule);
        activeModuleView.setValue(newModule.activate());
      }
    });
  }

  private void setupCleanup() {
    Platform.runLater(() -> {
      Scene scene = getScene();
      // if there is no scene, don't cause NPE by calling "getWindow()" on null
      if (Objects.isNull(scene)) {
        // should only be thrown in tests with mocked views
        LOGGER.error("setupCleanup - Scene could not be found! setOnCloseRequest was not set");
        return;
      }

      Stage stage = (Stage) getScene().getWindow();
      // when application is closed, destroy all modules
      stage.setOnCloseRequest(event -> {
        LOGGER.trace("Stage was requested to be closed - Close all open modules first");

        // must be implemented by using "while" since the list of getOpenModules changes when
        // modules are closed!
        while (getOpenModules().size() > 0) {
          WorkbenchModule moduleToClose = getOpenModules().get(0);
          LOGGER.trace("Cleanup - Close module: " + moduleToClose);
          if (!closeModule(moduleToClose)) {
            LOGGER.debug(
                String.format("Module %s prevented closing of the application", moduleToClose)
            );
            // module can't be destroyed yet - prevent closing of the application
            event.consume();
            // stop the closing of modules to proceed
            break;
          }
        }
      });
    });
  }

  /**
   * Opens the {@code module} in a new tab, if it isn't initialized yet or else opens the tab of
   * it.
   *
   * @param module the module to be opened or null to go to the home view
   */
  public void openModule(WorkbenchModule module) {
    if (!modules.contains(module)) {
      throw new IllegalArgumentException(
          "Module has not been loaded yet");
    }
    LOGGER.trace("openModule - set active module to " + module);
    activeModule.setValue(module);
  }

  /**
   * Goes back to the home screen where the user can choose between modules.
   */
  public void openHomeScreen() {
    activeModule.setValue(null);
  }

  /**
   * Closes the {@code module}.
   *
   * @param module to be closed
   * @return true if closing was successful
   */
  public boolean closeModule(WorkbenchModule module) {
    LOGGER.trace("closeModule - " + module);
    LOGGER.trace("closeModule - List of open modules: " + openModules);
    Objects.requireNonNull(module);
    int i = openModules.indexOf(module);
    if (i == -1) {
      throw new IllegalArgumentException("Module has not been opened yet.");
    }
    // set new active module
    WorkbenchModule oldActive = getActiveModule();
    WorkbenchModule newActive;
    if (oldActive != module) {
      // if we are not closing the currently active module, stay at the current
      newActive = oldActive;
    } else if (openModules.size() == 1) {
      // go to home screen
      newActive = null;
      LOGGER.trace("closeModule - Next active: Home Screen");
    } else if (i == 0) {
      // multiple modules open, leftmost is active
      newActive = openModules.get(i + 1);
      LOGGER.trace("closeModule - Next active: Next Module - " + newActive);
    } else {
      newActive = openModules.get(i - 1);
      LOGGER.trace("closeModule - Next active: Previous Module - " + newActive);
    }
    // attempt to destroy module
    if (!module.destroy()) {
      // module should or could not be destroyed
      LOGGER.trace("closeModule - Destroy: Fail - " + module);
      return false;
    } else {
      LOGGER.trace("closeModule - Destroy: Success - " + module);
      boolean removal = openModules.remove(module);
      LOGGER.trace("closeModule - Destroy, Removal successful: " + removal + " - " + module);
      if (oldActive != newActive) {
        // only log if the active module has been changed
        LOGGER.trace("closeModule - Set active module to: " + newActive);
      }
      activeModule.setValue(newActive);
      return removal;
    }
  }

  /**
   * Calculates the amount of pages of modules (rendered as tiles).
   *
   * @return amount of pages
   * @implNote Each page is filled up until there are as many tiles as {@code modulesPerPage}. This
   *           is repeated until all modules are rendered as tiles.
   */
  private int calculateAmountOfPages() {
    int amountOfModules = getModules().size();
    int modulesPerPage = getModulesPerPage();
    // if all pages are completely full
    if (amountOfModules % modulesPerPage == 0) {
      return amountOfModules / modulesPerPage;
    } else {
      // if the last page is not full, round up to the next page
      return amountOfModules / modulesPerPage + 1;
    }
  }

  public ObservableList<WorkbenchModule> getOpenModules() {
    return FXCollections.unmodifiableObservableList(openModules);
  }

  /**
   * Returns a list of the currently loaded modules.
   * @implNote Use this method to add or remove modules at runtime.
   */
  public ObservableList<WorkbenchModule> getModules() {
    return modules;
  }

  public WorkbenchModule getActiveModule() {
    return activeModule.get();
  }

  public ReadOnlyObjectProperty<WorkbenchModule> activeModuleProperty() {
    return activeModule;
  }

  public Node getActiveModuleView() {
    return activeModuleView.get();
  }

  public ReadOnlyObjectProperty<Node> activeModuleViewProperty() {
    return activeModuleView;
  }

  /**
   * Returns a list of the currently loaded toolbar controls on the left.
   * @implNote Use this method to add or remove toolbar controls on the left at runtime.
   */
  public ObservableSet<Node> getToolbarControlsLeft() {
    return toolbarControlsLeft;
  }

  /**
   * Returns a list of the currently loaded toolbar controls on the right.
   * @implNote Use this method to add or remove toolbar controls on the right at runtime.
   */
  public ObservableSet<Node> getToolbarControlsRight() {
    return toolbarControlsRight;
  }

  /**
   * Returns a map of all overlays, which have previously been opened, with their corresponding
   * {@link GlassPane}.
   */
  public ObservableMap<Node, GlassPane> getOverlays() {
    return FXCollections.unmodifiableObservableMap(overlays);
  }

  /**
   * Shows the {@code overlay} on top of the view, with a {@link GlassPane} in the background.
   *
   * @param overlay  to be shown
   * @param blocking If false (non-blocking), clicking outside of the {@code overlay} will cause it
   *                 to get hidden, together with its {@link GlassPane}. If true (blocking),
   *                 clicking outside of the {@code overlay} will not do anything. The {@code
   *                 overlay} itself must call {@link Workbench#hideOverlay(Node)} to hide
   *                 it.
   */
  public boolean showOverlay(Node overlay, boolean blocking) {
    LOGGER.trace("showOverlay");
    if (!overlays.containsKey(overlay)) {
      overlays.put(overlay, new GlassPane());
    }
    if (blocking) {
      return blockingOverlaysShown.add(overlay);
    } else {
      return nonBlockingOverlaysShown.add(overlay);
    }
  }

  /**
   * Hides the {@code overlay} together with its {@link GlassPane}, which has previously been shown
   * using {@link Workbench#showOverlay(Node, boolean)}.
   *
   * @param overlay  to be hidden
   * @implNote As the method's name implies, this will only <b>hide</b> the {@code overlay}, not
   *           remove it from the scene graph entirely.
   *           If keeping the {@code overlay} loaded hidden in the scene graph is not possible due
   *           to performance reasons, call {@link Workbench#clearOverlays()} after this method.
   */
  public boolean hideOverlay(Node overlay) {
    LOGGER.trace("hideOverlay");
    if (blockingOverlaysShown.contains(overlay)) {
      return blockingOverlaysShown.remove(overlay);
    } else {
      return nonBlockingOverlaysShown.remove(overlay);
    }
  }

  /**
   * Removes all previously loaded overlays from the scene graph including all references to them,
   * in order to free up memory.
   */
  public void clearOverlays() {
    LOGGER.trace("clearOverlays");
    nonBlockingOverlaysShown.clear();
    blockingOverlaysShown.clear();
    overlays.clear();
  }

  public void showNavigationDrawer() {
    showOverlay(navigationDrawer.get(), false);
  }

  public void hideNavigationDrawer() {
    hideOverlay(navigationDrawer.get());
  }

  public ObjectProperty<NavigationDrawer> navigationDrawerProperty() {
    return navigationDrawer;
  }

  public NavigationDrawer getNavigationDrawer() {
    return navigationDrawer.get();
  }

  public void setNavigationDrawer(NavigationDrawer navigationDrawer) {
    this.navigationDrawer.set(navigationDrawer);
  }

  public ObservableList<MenuItem> getNavigationDrawerItems() {
    return navigationDrawerItems;
  }

  public ObservableSet<Node> getNonBlockingOverlaysShown() {
    return FXCollections.unmodifiableObservableSet(nonBlockingOverlaysShown);
  }

  public ObservableSet<Node> getBlockingOverlaysShown() {
    return FXCollections.unmodifiableObservableSet(blockingOverlaysShown);
  }

  public int getModulesPerPage() {
    return modulesPerPage.get();
  }

  public void setModulesPerPage(int modulesPerPage) {
    this.modulesPerPage.set(modulesPerPage);
  }

  public Callback<Workbench, Tab> getTabFactory() {
    return tabFactory.get();
  }

  public ObjectProperty<Callback<Workbench, Tab>> tabFactoryProperty() {
    return tabFactory;
  }

  public void setTabFactory(Callback<Workbench, Tab> tabFactory) {
    this.tabFactory.set(tabFactory);
  }

  public Callback<Workbench, Tile> getTileFactory() {
    return tileFactory.get();
  }

  public ObjectProperty<Callback<Workbench, Tile>> tileFactoryProperty() {
    return tileFactory;
  }

  public void setTileFactory(Callback<Workbench, Tile> tileFactory) {
    this.tileFactory.set(tileFactory);
  }

  public Callback<Workbench, Page> getPageFactory() {
    return pageFactory.get();
  }

  public ObjectProperty<Callback<Workbench, Page>> pageFactoryProperty() {
    return pageFactory;
  }

  public void setPageFactory(Callback<Workbench, Page> pageFactory) {
    this.pageFactory.set(pageFactory);
  }

  public IntegerProperty modulesPerPageProperty() {
    return modulesPerPage;
  }

  public int getAmountOfPages() {
    return amountOfPages.get();
  }

  public ReadOnlyIntegerProperty amountOfPagesProperty() {
    return amountOfPages;
  }

  @Override
  public String getUserAgentStylesheet() {
    return Workbench.class.getResource("css/main.css").toExternalForm();
  }
}