package fr.aym.gtwmap.api;

import lombok.AllArgsConstructor;
import net.minecraft.entity.Entity;

public interface ITrackableObject<T> {
    T getTrackedObject();

    float getPosX();

    float getPosZ();

    String getDisplayName();

    String getIcon();

    default int renderPoliceCircleAroundRadius() {
        return 0;
    }

    @AllArgsConstructor
    class TrackedEntity implements ITrackableObject<Entity> {
        private final Entity entity;
        private final String displayName;
        private final String icon;

        @Override
        public Entity getTrackedObject() {
            return entity;
        }

        @Override
        public float getPosX() {
            return (float) this.entity.posX;
        }

        @Override
        public float getPosZ() {
            return (float) this.entity.posZ;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getIcon() {
            return icon;
        }

        @Override
        public int hashCode() {
            return entity.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ITrackableObject && entity.equals(((ITrackableObject<?>) obj).getTrackedObject());
        }
    }

    class TrackedObjectWrapper implements ITrackableObject<Object> {
        private final Object object;

        protected TrackedObjectWrapper(Object object) {
            this.object = object;
        }

        @Override
        public Object getTrackedObject() {
            return object;
        }

        @Override
        public float getPosX() {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getPosZ() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDisplayName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getIcon() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            return object.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ITrackableObject && object.equals(((ITrackableObject<?>) obj).getTrackedObject());
        }
    }
}
