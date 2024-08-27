package cn.ksmcbrigade.KCATKA;

import cn.qianzi.network.KillcupModVariables;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Objects;
import java.util.Random;

@Mod(KCATKAddon.MODID)
@Mod.EventBusSubscriber(modid = KCATKAddon.MODID)
public class KCATKAddon {

    public static final String MODID = "kca_tka";
    public static final SimpleChannel channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(MODID,"killaura_channel"),()->"345",(a)->true,(b)->true);

    private static final Random random = new Random();

    public static float range = 6;
    public static int times = 4;
    public static boolean swing = true;

    public KCATKAddon() {
        MinecraftForge.EVENT_BUS.register(this);
        channel.registerMessage(0,Message.class,Message::encode,Message::decode,((message, contextSupplier) -> {
            try {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->{
                    //turn
                    try {
                        Objects.requireNonNull(Minecraft.getInstance().getConnection()).send(new ServerboundMovePlayerPacket.Rot(random.nextFloat(-180,180),random.nextFloat(-180,180),Boolean.parseBoolean(message.message)));
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.setYBodyRot(random.nextFloat(-180,180));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //killaura
                    try {
                        Minecraft.getInstance().level.getEntitiesOfClass(Entity.class,new AABB(Minecraft.getInstance().player.getPosition(0),Minecraft.getInstance().player.getPosition(0)).inflate(Math.max(Minecraft.getInstance().gameMode.getPickRange(),KCATKAddon.range)),Entity::isAttackable).stream().filter(e->e.getId()!=Minecraft.getInstance().player.getId()).toList().forEach(entity -> new Thread(()->{
                            for (int i = 0; i < KCATKAddon.times; i++) {
                                try {
                                    Minecraft.getInstance().getConnection().send(ServerboundInteractPacket.createAttackPacket(entity,false));
                                    if(swing){
                                        Minecraft.getInstance().getConnection().send(new ServerboundSwingPacket(Minecraft.getInstance().player.getUsedItemHand()));
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            contextSupplier.get().setPacketHandled(true);
        }));
        System.out.println("Channel registered loaded.");
        System.out.println("KCATKA Addon loaded.");
    }

    public static class Message {
        public final String message;

        public Message(String message) {
            this.message = message;
        }

        public Message(boolean b) {
            this.message = String.valueOf(b);
        }

        public String getMessage() {
            return message;
        }

        public static void encode(Message msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.message);
        }

        public static Message decode(FriendlyByteBuf buf) {return new Message(buf.readUtf());
        }
    }

    /*@SubscribeEvent
    public void command(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("test-aura").executes(context -> {
            ServerPlayer player = context.getSource().getPlayer();
            KCATKAddon.channel.sendTo(new KCATKAddon.Message(player.onGround()),player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            return 0;
        }));
    }*/

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void config(RegisterClientCommandsEvent event){
        event.getDispatcher().register(Commands.literal("atk-config").executes(context -> {
            context.getSource().sendSystemMessage(Component.literal("range: "+range));
            context.getSource().sendSystemMessage(Component.literal("times: "+times));
            context.getSource().sendSystemMessage(Component.literal("swing: "+swing));
            return 0;
        }).then(Commands.argument("range", FloatArgumentType.floatArg(0)).executes(context -> {
            range = FloatArgumentType.getFloat(context,"range");
            context.getSource().sendSystemMessage(CommonComponents.GUI_DONE);
            return 0;
        }).then(Commands.argument("times", IntegerArgumentType.integer(0)).executes(context -> {
            range = FloatArgumentType.getFloat(context,"range");
            times = IntegerArgumentType.getInteger(context,"times");
            context.getSource().sendSystemMessage(CommonComponents.GUI_DONE);
            return 0;
        }).then(Commands.argument("swing", BoolArgumentType.bool()).executes(context -> {
            range = FloatArgumentType.getFloat(context,"range");
            times = IntegerArgumentType.getInteger(context,"times");
            swing = BoolArgumentType.getBool(context,"swing");
            context.getSource().sendSystemMessage(CommonComponents.GUI_DONE);
            return 0;
        })))));
    }

    @SubscribeEvent
    @OnlyIn(Dist.DEDICATED_SERVER)
    public static void tick(TickEvent.PlayerTickEvent event){
        Entity entity = event.player;
        Level world = event.player.level();
        if (entity.getCapability(KillcupModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new KillcupModVariables.PlayerVariables()).isOn) {
            if(entity instanceof ServerPlayer player){
                KCATKAddon.channel.sendTo(new KCATKAddon.Message(player.onGround()),player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
            else if(entity instanceof Player other){
                try {
                    world.getEntitiesOfClass(Entity.class,new AABB(other.getPosition(0),other.getPosition(0)).inflate(Math.max(5, KCATKAddon.range)),Entity::isAttackable).stream().filter(e->e.getId()!=other.getId()).toList().forEach(entity2 -> {
                        for (int i = 0; i < KCATKAddon.times; i++) {
                            try {
                                other.attack(entity2);
                                if(KCATKAddon.swing){
                                    other.swing(other.getUsedItemHand());
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
