/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package automenta.netention.gui;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import automenta.netention.NObject;

/**
 *
 * @author me
 */
public class ObjectCard extends VBox {
    
    public ObjectCard(NObject n) {
        super();
        
        Label nameLabel = new Label(n.name());
        nameLabel.setFont(Font.getDefault().font(24f));
        getChildren().add(nameLabel);
        
        //getChildren().add(new Label(n.getTags().toString()));
        getChildren().add(new Label(n.toString()));
    }
}