package gg.galaxygaming.gasconduits.common.conduit.ender;

import com.enderio.core.common.util.RoundRobinIterator;
import crazypants.enderio.base.conduit.item.FunctionUpgrade;
import crazypants.enderio.base.conduit.item.ItemFunctionUpgrade;
import crazypants.enderio.conduits.conduit.AbstractConduitNetwork;
import gg.galaxygaming.gasconduits.GasConduitConfig;
import gg.galaxygaming.gasconduits.GasConduitsConstants;
import gg.galaxygaming.gasconduits.common.conduit.IGasConduit;
import gg.galaxygaming.gasconduits.common.conduit.NetworkTank;
import gg.galaxygaming.gasconduits.common.filter.IGasFilter;
import gg.galaxygaming.gasconduits.common.utils.GasUtil;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import java.util.*;

public class EnderGasConduitNetwork extends AbstractConduitNetwork<IGasConduit, EnderGasConduit> {
    List<NetworkTank> tanks = new ArrayList<>();
    Map<NetworkTankKey, NetworkTank> tankMap = new HashMap<>();
    Map<NetworkTank, RoundRobinIterator<NetworkTank>> iterators;

    boolean filling;

    public EnderGasConduitNetwork() {
        super(EnderGasConduit.class, IGasConduit.class);
    }

    public void connectionChanged(@Nonnull EnderGasConduit con, @Nonnull EnumFacing conDir) {
        NetworkTankKey key = new NetworkTankKey(con, conDir);
        NetworkTank tank = new NetworkTank(con, conDir);

        // Check for later
        boolean sort = false;
        NetworkTank oldTank = tankMap.get(key);
        if (oldTank != null && oldTank.getPriority() != tank.getPriority()) {
            sort = true;
        }

        tanks.remove(tank); // remove old tank, NB: =/hash is only calced on location and dir
        tankMap.remove(key);
        tanks.add(tank);
        tankMap.put(key, tank);

        // If the priority has been changed, then sort the list to match
        if (sort) {
            tanks.sort((arg0, arg1) -> arg1.getPriority() - arg0.getPriority());
        }
    }

    public boolean extractFrom(@Nonnull EnderGasConduit con, @Nonnull EnumFacing conDir) {
        NetworkTank tank = getTank(con, conDir);
        if (!tank.isValid() || tank.getExternalTank() == null) {
            return false;
        }

        GasStack drained = GasUtil.getGasStack(tank.getExternalTank());

        if (!matchedFilter(drained, con, conDir, true) || !tank.getExternalTank().canDrawGas(tank.getConduitDir(), drained.getGas())) {
            return false;
        }

        drained.amount = Math.min(drained.amount, GasConduitConfig.tier3_extractRate * getExtractSpeedMultiplier(tank) / 2);
        int amountAccepted = fillFrom(tank, drained.copy(), true);
        if (amountAccepted <= 0) {
            return false;
        }
        drained.amount = amountAccepted;
        drained = tank.getExternalTank().drawGas(tank.getConduitDir(), drained.amount, true);
        return drained != null && drained.amount > 0;
    }

    @Nonnull
    private NetworkTank getTank(@Nonnull EnderGasConduit con, @Nonnull EnumFacing conDir) {
        return tankMap.get(new NetworkTankKey(con, conDir));
    }

    public int fillFrom(@Nonnull EnderGasConduit con, @Nonnull EnumFacing conDir, GasStack resource, boolean doFill) {
        return fillFrom(getTank(con, conDir), resource, doFill);
    }

    public int fillFrom(@Nonnull NetworkTank tank, GasStack resource, boolean doFill) {
        if (filling) {
            return 0;
        }

        try {
            filling = true;

            if (!matchedFilter(resource, tank.getConduit(), tank.getConduitDir(), true)) {
                return 0;
            }

            resource = resource.copy();
            resource.amount = Math.min(resource.amount, GasConduitConfig.tier3_maxIO * getExtractSpeedMultiplier(tank) / 2);
            int filled = 0;
            int remaining = resource.amount;
            // TODO: Only change starting pos of iterator is doFill is true so a false then true returns the same

            for (NetworkTank target : getIteratorForTank(tank)) {
                if (target.getExternalTank() != null && (!target.equals(tank) || tank.isSelfFeed()) && target.acceptsOutput() && target.isValid() && target.getInputColor() == tank.getOutputColor()
                        && matchedFilter(resource, target.getConduit(), target.getConduitDir(), false) && target.getExternalTank().canReceiveGas(target.getConduitDir().getOpposite(), resource.getGas())) {
                    int vol = target.getExternalTank().receiveGas(target.getConduitDir().getOpposite(), resource.copy(), doFill);
                    remaining -= vol;
                    filled += vol;
                    if (remaining <= 0) {
                        return filled;
                    }
                    resource.amount = remaining;
                }
            }
            return filled;

        } finally {
            if (!tank.isRoundRobin()) {
                getIteratorForTank(tank).reset();
            }
            filling = false;
        }
    }

    private int getExtractSpeedMultiplier(NetworkTank tank) {
        int extractSpeedMultiplier = 2;

        ItemStack upgradeStack = tank.getConduit().getFunctionUpgrade(tank.getConduitDir());
        if (!upgradeStack.isEmpty()) {
            FunctionUpgrade upgrade = ItemFunctionUpgrade.getFunctionUpgrade(upgradeStack);
            if (upgrade == FunctionUpgrade.EXTRACT_SPEED_UPGRADE) {
                extractSpeedMultiplier += GasConduitsConstants.GAS_MAX_EXTRACTED_SCALER * Math.min(upgrade.maxStackSize, upgradeStack.getCount());
            } else if (upgrade == FunctionUpgrade.EXTRACT_SPEED_DOWNGRADE) {
                extractSpeedMultiplier = 1;
            }
        }

        return extractSpeedMultiplier;
    }

    private boolean matchedFilter(GasStack drained, @Nonnull EnderGasConduit con, @Nonnull EnumFacing conDir, boolean isInput) {
        if (drained == null) {
            return false;
        }
        IGasFilter filter = con.getFilter(conDir, isInput);
        return filter == null || filter.isEmpty() || filter.matchesFilter(drained);
    }

    private RoundRobinIterator<NetworkTank> getIteratorForTank(@Nonnull NetworkTank tank) {
        if (iterators == null) {
            iterators = new HashMap<>();
        }
        RoundRobinIterator<NetworkTank> res = iterators.get(tank);
        if (res == null) {
            res = new RoundRobinIterator<>(tanks);
            iterators.put(tank, res);
        }
        return res;
    }

    public GasTankInfo[] getTankProperties(@Nonnull EnderGasConduit con, @Nonnull EnumFacing conDir) {
        List<GasTankInfo> res = new ArrayList<>(tanks.size());
        NetworkTank tank = getTank(con, conDir);
        for (NetworkTank target : tanks) {
            if (!target.equals(tank) && target.isValid() && target.getExternalTank() != null) {
                res.addAll(Arrays.asList(target.getExternalTank().getTankInfo()));
            }
        }
        return res.toArray(new GasTankInfo[0]);
    }

    static class NetworkTankKey {
        EnumFacing conDir;
        BlockPos conduitLoc;

        public NetworkTankKey(@Nonnull EnderGasConduit con, @Nonnull EnumFacing conDir) {
            this(con.getBundle().getLocation(), conDir);
        }

        public NetworkTankKey(@Nonnull BlockPos conduitLoc, @Nonnull EnumFacing conDir) {
            this.conDir = conDir;
            this.conduitLoc = conduitLoc;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = prime + ((conDir == null) ? 0 : conDir.hashCode());
            return prime * result + ((conduitLoc == null) ? 0 : conduitLoc.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            NetworkTankKey other = (NetworkTankKey) obj;
            if (conDir != other.conDir) {
                return false;
            }
            if (conduitLoc == null) {
                return other.conduitLoc == null;
            }
            return conduitLoc.equals(other.conduitLoc);
        }
    }
}