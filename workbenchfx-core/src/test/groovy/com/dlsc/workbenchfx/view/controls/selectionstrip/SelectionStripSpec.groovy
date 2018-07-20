package com.dlsc.workbenchfx.view.controls.selectionstrip

import com.dlsc.workbenchfx.model.WorkbenchModule
import com.dlsc.workbenchfx.testing.MockSelectionStrip
import javafx.scene.Scene
import javafx.stage.Stage
import javafx.util.Callback
import org.testfx.api.FxRobot
import org.testfx.framework.spock.ApplicationSpec
import spock.lang.Shared
import spock.lang.Unroll

class SelectionStripSpec extends ApplicationSpec {

    private SelectionStrip<WorkbenchModule> selectionStrip
    private FxRobot robot

    @Shared
    private WorkbenchModule workbenchModule = Mock()
    @Shared
    private WorkbenchModule workbenchModule2 = Mock()

    @Override
    void start(Stage stage) throws Exception {
        selectionStrip = new MockSelectionStrip()
        robot = new FxRobot()

        Scene scene = new Scene(selectionStrip, 100, 100)
        stage.setScene(scene)
        stage.show()
    }

    def "test if styleclass was set correctly"() {
        given: "String of styleclass which shall be set"
        String styleClass = "selection-strip"

        when: "selectionStrip was initialized"

        then: "styleclass must be set"
        selectionStrip.getStyleClass().contains(styleClass)
    }

    def "test if maxWidth and Height were set correctly"() {
        given: "Double value of max and min which shall be set"
        double prefWidth = 400;
        double prefHeight = 50;

        when: "selectionStrip was initialized"

        then: "max and min must be set"
        prefWidth == selectionStrip.getPrefWidth()
        prefHeight == selectionStrip.getPrefHeight()
    }

    def "set a cell factory"() {
        when: "selectionStrip was initialized"

        then: "cellfactory is instance of StripCell"
        selectionStrip.getCellFactory() instanceof Callback<SelectionStrip, StripCell<WorkbenchModule>>
    }

    def "tests selecteditem listener: behaviour of adding a module and setting it selected"(
            boolean autoscrolling, WorkbenchModule module, WorkbenchModule selectedModule, int listSize) {
        given:
        robot.interact {
            selectionStrip.setAutoScrolling(autoscrolling)
            selectionStrip.getItems().add(module)
        }

        expect:
        robot.interact {
            selectedModule == selectionStrip.getSelectedItem()
            listSize == selectionStrip.getItems().size()
            selectedModule == selectionStrip.getProperties().get("scroll.to");
        }

        where:
        autoscrolling | module          || selectedModule  | listSize
        true          | null            || null            | 0
        true          | workbenchModule || workbenchModule | 1
        false         | null            || null            | 0
        false         | workbenchModule || null            | 1
    }

    @Shared
    SelectionStrip<WorkbenchModule> selectionStripShared

    def "tests behaviour of adding multiple modules and setting them selected"(WorkbenchModule module, WorkbenchModule selectedModule, int listSize) {
        given:
        robot.interact {
            selectionStripShared = new MockSelectionStrip<>()
            selectionStripShared.getItems().add(module)
        }

        expect:
        robot.interact {
            selectedModule == selectionStrip.getSelectedItem()
            listSize == selectionStrip.getItems().size()
            selectedModule == selectionStrip.getProperties().get("scroll.to");
        }

        where:
        module           || selectedModule   | listSize
        null             || null             | 0
        workbenchModule  || workbenchModule  | 1
        workbenchModule2 || workbenchModule2 | 2
        workbenchModule2 || workbenchModule2 | 3
    }

    def "test removing indicees from items"(
            WorkbenchModule[] initialModules,
            int[] removeIndicees,
            WorkbenchModule activeModule,
            int expectedSize
    ) {
        given: "selectionStrip with two items"
        robot.interact {
            selectionStrip.getItems().addAll(initialModules)
        }

        when: "removing the items"
        robot.interact {
            for (int i = 0; i < removeIndicees.length; i++) {
                selectionStrip.getItems().remove(removeIndicees[i])
                // decrease the index of all other values, cause the items-size decreased
                for (int j = i + 1; j < removeIndicees.length; j++) {
                    removeIndicees[j]--
                }
            }
        }

        then: "the selectionStrip contains"
        robot.interact {
            activeModule == selectionStrip.getSelectedItem()
            expectedSize == selectionStrip.getItems().size()
        }

        where:
        initialModules                                       | removeIndicees || activeModule     | expectedSize
        [workbenchModule, workbenchModule2]                  | [1]            || workbenchModule  | 1
        [workbenchModule, workbenchModule2]                  | [0, 1]         || null             | 0
        [workbenchModule, workbenchModule2, workbenchModule] | [0, 2]         || workbenchModule2 | 1
        [workbenchModule, workbenchModule2, workbenchModule] | [2, 1]         || workbenchModule  | 1
        [workbenchModule]                                    | [0]            || null             | 0
        [workbenchModule, workbenchModule2]                  | [0]            || workbenchModule2 | 1
    }
}
