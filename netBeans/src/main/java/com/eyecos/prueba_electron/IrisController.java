package com.eyecos.prueba_electron;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import com.eyecos.prueba_electron.IrisSegmentation.IrisData;
import com.google.gson.Gson;

public class IrisController {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Uso: java -jar prueba_electron.jar <ruta_imagen_o_base64>");
            System.exit(1);
        }
        
        String input = args[0];
        BufferedImage image = null;
        
        try {
            if (input.startsWith("data:image")) {
                String base64Image = input.split(",")[1];
                byte[] imageBytes = Base64.getDecoder().decode(base64Image);
                image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            } else {
                image = ImageIO.read(new File(input));
            }
            
            IrisData irisData = IrisSegmentation.segmentIris(image);
            
            Gson gson = new Gson();
            String jsonOutput = gson.toJson(irisData);
            System.out.println(jsonOutput);
            
        } catch (IOException e) {
            System.err.println("Error al procesar la imagen: " + e.getMessage());
            System.exit(1);
        }
    }
}