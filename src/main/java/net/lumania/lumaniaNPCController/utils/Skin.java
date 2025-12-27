package net.lumania.lumaniaNPCController.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.URL;

public class Skin {

    public static String[] getSkin(String name) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            InputStreamReader reader = new InputStreamReader(url.openStream());
            String uuid = new JsonParser().parse(reader).getAsJsonObject().get("id").getAsString();

            URL url2 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            InputStreamReader reader2 = new InputStreamReader(url2.openStream());
            JsonObject property = new JsonParser().parse(reader2).getAsJsonObject().getAsJsonArray("properties").get(0).getAsJsonObject();
            String texture = property.get("value").getAsString();
            String signature = property.get("signature").getAsString();
            return new String[]{texture, signature};
        } catch (Exception e) {
            // Default-Skin, falls Mojang API fehlschlägt
            String texture = "ewogICJ0aW1lc3RhbXAiIDogMTcyMTEzNDQ2MjExNywKICAicHJvZmlsZUlkIiA6ICIzZWQ2ZWYwNzU3NmE0ZjU4Yjk4NDRiZDI5MzMyODQzMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJsdWlzMzEzMSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMmMwZDc2MzUwZWExMjU1YzYxNTNjZDhjYzRlYjAxZmM5MTkwNGRiZDQ0ZWM5YTZjMWIxYjA3MWRlYmYxY2ZjIgogICAgfQogIH0KfQ";
            String signature = "N3gJvuz2KSbiEbMEvZ/tusVKPh8Btnu5SCcDvyJvU6LxVe2zA61aeav2IBBBCS19rww4DBqJaXVq7ema9ns8iCYj3vQf+MGJWf0cZV87lQtS+/XisO0NN6U4yngCIujI7VHC6VuBsGeEH9ONiPnFog84F21Tet9eV6vnvRVzHCXpsV0xNv25Vlc5roPVP5nSQTKqxHI2QYhdC9KOX7jwXF4GVF6/FSEpa/SjIjZU3nU72QOqcDvz+3PQQePQ1eHDui2O0ruU+XbPQwFf/1YPuE+YQi4gthzQKE4hJqzLIQ5nJiKN9ztLj7JIbA6gpP7IRTj874m3id8F+MY12qa36Dej4+VE+5vIC2ibrQy8aTSlAOIg/xi/+mbGPmns3h5YlifbO+4wZupsTpL0D1xlfua3GNPx4CtX1y/X2db9x8YasnqvZkGTHIPzpVQ1iExk2zyxLrgvenflBz4pKzv/ulLD6Ow2IhpmYu4jYrgxw+iQlf3QLhpvgE8ihNnTIf1yaoWDEzgxIPmaMwS3bDCRWNZFgmBkznIOd8w++YiGZoUb+BTTPLWFyGaGICmV6KQ5bAeJhe4Q1cwhmNOrYDJERTmbvbIde2PB+C9hw6fD6zFd+QR2+gz6vvIelPxxTYigvsPiFJgBr9GlyOdV9A8fAlr8ZAvdiE9/JqSQJaARViw=";
            return new String[]{texture, signature};
        }
    }
}
