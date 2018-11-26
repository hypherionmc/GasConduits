package gg.galaxygaming.gasconduits.conduit;

import com.enderio.core.common.util.DyeColor;
import crazypants.enderio.base.conduit.ConnectionMode;
import mekanism.api.gas.IGasHandler;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;

public class NetworkTank {
    @Nonnull
    final EnderGasConduit con;
    @Nonnull
    final EnumFacing conDir;
    final IGasHandler externalTank;
    @Nonnull
    final EnumFacing tankDir;
    @Nonnull
    final BlockPos conduitLoc;
    final boolean acceptsOutput;
    final DyeColor inputColor;
    final DyeColor outputColor;
    final int priority;
    final boolean roundRobin;
    final boolean selfFeed;

    public NetworkTank(@Nonnull EnderGasConduit con, @Nonnull EnumFacing conDir) {
        this.con = con;
        this.conDir = conDir;
        conduitLoc = con.getBundle().getLocation();
        tankDir = conDir.getOpposite();
        externalTank = AbstractGasConduit.getExternalGasHandler(con.getBundle().getBundleworld(), conduitLoc.offset(conDir), tankDir);
        acceptsOutput = con.getConnectionMode(conDir).acceptsOutput();
        inputColor = con.getOutputColor(conDir);
        outputColor = con.getInputColor(conDir);
        priority = con.getOutputPriority(conDir);
        roundRobin = con.isRoundRobinEnabled(conDir);
        selfFeed = con.isSelfFeedEnabled(conDir);
    }

    public boolean isValid() {
        return externalTank != null && con.getConnectionMode(conDir) != ConnectionMode.DISABLED;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = prime + conDir.hashCode();
        return prime * result + conduitLoc.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        NetworkTank other = (NetworkTank) obj;
        if (conDir != other.conDir) {
            return false;
        }
        return conduitLoc.equals(other.conduitLoc);
    }
}