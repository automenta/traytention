package jnetention.gui.geo;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.awt.WorldWindowGLJPanel;
import gov.nasa.worldwind.event.RenderingExceptionListener;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.exception.WWAbsentRequirementException;
import gov.nasa.worldwind.layers.*;
import gov.nasa.worldwind.layers.placename.PlaceNameLayer;
import gov.nasa.worldwind.util.StatisticsPanel;
import gov.nasa.worldwind.util.StatusBar;
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwindx.examples.ClickAndGoSelectListener;
import gov.nasa.worldwindx.examples.LayerPanel;
import gov.nasa.worldwindx.examples.util.HighlightController;

import javax.swing.*;
import java.awt.*;


/**
 * Created by me on 5/22/15.
 */
public class WWMap extends JPanel {

    public static class AppPanel extends JPanel
    {
        protected WorldWindow wwd;
        protected StatusBar statusBar;
        //protected ToolTipController toolTipController;
        protected HighlightController highlightController;

        public AppPanel(Dimension canvasSize, boolean includeStatusBar)
        {
            super(new BorderLayout());

            this.wwd = this.createWorldWindow();
            ((Component) this.wwd).setPreferredSize(canvasSize);

            // Create the default model as described in the current worldwind properties.
            Model m = (Model) WorldWind.createConfigurationComponent(AVKey.MODEL_CLASS_NAME);
            this.wwd.setModel(m);

            // Setup a select listener for the worldmap click-and-go feature
            this.wwd.addSelectListener(new ClickAndGoSelectListener(this.getWwd(), WorldMapLayer.class));

            this.add((Component) this.wwd, BorderLayout.CENTER);
            if (includeStatusBar)
            {
                this.statusBar = new StatusBar();
                this.add(statusBar, BorderLayout.PAGE_END);
                this.statusBar.setEventSource(wwd);
            }

            // Add controllers to manage highlighting and tool tips.
            //this.toolTipController = new ToolTipController(this.getWwd(), AVKey.DISPLAY_NAME, null);
            this.highlightController = new HighlightController(this.getWwd(), SelectEvent.ROLLOVER);
        }

        protected WorldWindow createWorldWindow()
        {
            return new WorldWindowGLJPanel();
        }

        public WorldWindow getWwd()
        {
            return wwd;
        }

        public StatusBar getStatusBar()
        {
            return statusBar;
        }
    }

    private Dimension canvasSize = new Dimension(800, 600);

    protected AppPanel wwjPanel;
    protected LayerPanel layerPanel;
    protected StatisticsPanel statsPanel;

    public WWMap()
    {
        this.initialize(true, true, false);
    }

    public WWMap(Dimension size)
    {
        this.canvasSize = size;
        this.initialize(true, true, false);
    }

    public WWMap(boolean includeStatusBar, boolean includeLayerPanel, boolean includeStatsPanel)
    {
        this.initialize(includeStatusBar, includeLayerPanel, includeStatsPanel);
    }

    protected void initialize(boolean includeStatusBar, boolean includeLayerPanel, boolean includeStatsPanel)
    {
        setLayout(new BorderLayout());

        // Create the WorldWindow.
        this.wwjPanel = this.createAppPanel(this.canvasSize, includeStatusBar);
        this.wwjPanel.setPreferredSize(canvasSize);

        // Put the pieces together.
        add(wwjPanel, BorderLayout.CENTER);
        if (includeLayerPanel)
        {
            this.layerPanel = new LayerPanel(this.wwjPanel.getWwd(), null);
            add(this.layerPanel, BorderLayout.WEST);
        }

        if (includeStatsPanel || System.getProperty("gov.nasa.worldwind.showStatistics") != null)
        {
            this.statsPanel = new StatisticsPanel(this.wwjPanel.getWwd(), new Dimension(250, canvasSize.height));
            add(this.statsPanel, BorderLayout.EAST);
        }

        // Create and install the view controls layer and register a controller for it with the World Window.
        ViewControlsLayer viewControlsLayer = new ViewControlsLayer();
        insertBeforeCompass(getWwd(), viewControlsLayer);
        this.getWwd().addSelectListener(new ViewControlsSelectListener(this.getWwd(), viewControlsLayer));

        // Register a rendering exception listener that's notified when exceptions occur during rendering.
        this.wwjPanel.getWwd().addRenderingExceptionListener(new RenderingExceptionListener()
        {
            public void exceptionThrown(Throwable t)
            {
                if (t instanceof WWAbsentRequirementException)
                {
                    String message = "Computer does not meet minimum graphics requirements.\n";
                    message += "Please install up-to-date graphics driver and try again.\n";
                    message += "Reason: " + t.getMessage() + "\n";
                    message += "This program will end when you press OK.";

                    JOptionPane.showMessageDialog(null, message, "Unable to Start Program",
                            JOptionPane.ERROR_MESSAGE);
                    System.exit(-1);
                }
            }
        });

        // Search the layer list for layers that are also select listeners and register them with the World
        // Window. This enables interactive layers to be included without specific knowledge of them here.
        for (Layer layer : this.wwjPanel.getWwd().getModel().getLayers())
        {
            if (layer instanceof SelectListener)
            {
                this.getWwd().addSelectListener((SelectListener) layer);
            }
        }



        // Center the application on the screen.
        WWUtil.alignComponent(null, this, AVKey.CENTER);

    }

    protected AppPanel createAppPanel(Dimension canvasSize, boolean includeStatusBar)
    {
        return new AppPanel(canvasSize, includeStatusBar);
    }

    public Dimension getCanvasSize()
    {
        return canvasSize;
    }

    public AppPanel getWwjPanel()
    {
        return wwjPanel;
    }

    public WorldWindow getWwd()
    {
        return this.wwjPanel.getWwd();
    }

    public StatusBar getStatusBar()
    {
        return this.wwjPanel.getStatusBar();
    }

    public LayerPanel getLayerPanel()
    {
        return layerPanel;
    }

    public StatisticsPanel getStatsPanel()
    {
        return statsPanel;
    }

        /*public void setToolTipController(ToolTipController controller)
        {
            if (this.wwjPanel.toolTipController != null)
                this.wwjPanel.toolTipController.dispose();

            this.wwjPanel.toolTipController = controller;
        }*/

    public void setHighlightController(HighlightController controller)
    {
        if (this.wwjPanel.highlightController != null)
            this.wwjPanel.highlightController.dispose();

        this.wwjPanel.highlightController = controller;
    }
    public static void insertBeforeCompass(WorldWindow wwd, Layer layer)
    {
        // Insert the layer into the layer list just before the compass.
        int compassPosition = 0;
        LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers)
        {
            if (l instanceof CompassLayer)
                compassPosition = layers.indexOf(l);
        }
        layers.add(compassPosition, layer);
    }

    public static void insertBeforePlacenames(WorldWindow wwd, Layer layer)
    {
        // Insert the layer into the layer list just before the placenames.
        int compassPosition = 0;
        LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers)
        {
            if (l instanceof PlaceNameLayer)
                compassPosition = layers.indexOf(l);
        }
        layers.add(compassPosition, layer);
    }

    public static void insertAfterPlacenames(WorldWindow wwd, Layer layer)
    {
        // Insert the layer into the layer list just after the placenames.
        int compassPosition = 0;
        LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers)
        {
            if (l instanceof PlaceNameLayer)
                compassPosition = layers.indexOf(l);
        }
        layers.add(compassPosition + 1, layer);
    }

    public static void insertBeforeLayerName(WorldWindow wwd, Layer layer, String targetName)
    {
        // Insert the layer into the layer list just before the target layer.
        int targetPosition = 0;
        LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers)
        {
            if (l.getName().indexOf(targetName) != -1)
            {
                targetPosition = layers.indexOf(l);
                break;
            }
        }
        layers.add(targetPosition, layer);
    }

    static
    {
        System.setProperty("java.net.useSystemProxies", "true");
        if (Configuration.isMacOS())
        {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "World Wind Application");
            System.setProperty("com.apple.mrj.application.growbox.intrudes", "false");
            System.setProperty("apple.awt.brushMetalLook", "true");
        }
        else if (Configuration.isWindowsOS())
        {
            System.setProperty("sun.awt.noerasebackground", "true"); // prevents flashing during window resizing
        }
    }
//
//    public static AppFrame start(String appName, Class appFrameClass)
//    {
//        if (Configuration.isMacOS() && appName != null)
//        {
//            System.setProperty("com.apple.mrj.application.apple.menu.about.name", appName);
//        }
//
//        try
//        {
//            final AppFrame frame = (AppFrame) appFrameClass.newInstance();
//            frame.setTitle(appName);
//            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//            java.awt.EventQueue.invokeLater(new Runnable()
//            {
//                public void run()
//                {
//                    frame.setVisible(true);
//                }
//            });
//
//            return frame;
//        }
//        catch (Exception e)
//        {
//            e.printStackTrace();
//            return null;
//        }
//    }

//    public static void main(String[] args)
//    {
//        // Call the static start method like this from the main method of your derived class.
//        // Substitute your application's name for the first argument.
//        WWMap.start("World Wind Application", AppFrame.class);
//    }

}
