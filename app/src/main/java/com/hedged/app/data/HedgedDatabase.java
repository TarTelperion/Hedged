package com.hedged.app.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Delete;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.Update;

import java.util.List;

@Database(
        entities = {
                HedgedDatabase.CategoryEntity.class,
                HedgedDatabase.TransactionEntity.class,
                HedgedDatabase.BudgetEntity.class,
                HedgedDatabase.GoalEntity.class,
                HedgedDatabase.GoalEntryEntity.class
        },
        version = 1,
        exportSchema = true
)
public abstract class HedgedDatabase extends RoomDatabase {

    private static volatile HedgedDatabase INSTANCE;

    public static HedgedDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (HedgedDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            HedgedDatabase.class,
                            "hedged.db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract CategoryDao categoryDao();
    public abstract TransactionDao transactionDao();
    public abstract BudgetDao budgetDao();
    public abstract GoalDao goalDao();
    public abstract GoalEntryDao goalEntryDao();

    @Entity(
            tableName = "categories",
            indices = {@Index(value = {"name"}, unique = true)}
    )
    public static class CategoryEntity {
        @PrimaryKey(autoGenerate = true)
        public long id;
        @NonNull public String name;
        @NonNull public String kind;
        @NonNull @ColumnInfo(name = "color_key") public String colorKey;
        @ColumnInfo(name = "is_active") public boolean isActive;
        @ColumnInfo(name = "sort_order") public int sortOrder;

        public CategoryEntity(long id, @NonNull String name, @NonNull String kind,
                              @NonNull String colorKey, boolean isActive, int sortOrder) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.colorKey = colorKey;
            this.isActive = isActive;
            this.sortOrder = sortOrder;
        }
    }

    @Entity(
            tableName = "transactions",
            foreignKeys = @ForeignKey(
                    entity = CategoryEntity.class,
                    parentColumns = "id",
                    childColumns = "category_id",
                    onDelete = ForeignKey.RESTRICT
            ),
            indices = {
                    @Index("category_id"),
                    @Index("date_epoch_day"),
                    @Index(value = {"category_id", "date_epoch_day"})
            }
    )
    public static class TransactionEntity {
        @PrimaryKey(autoGenerate = true)
        public long id;
        @ColumnInfo(name = "date_epoch_day") public long dateEpochDay;
        @ColumnInfo(name = "category_id") public long categoryId;
        @NonNull public String type;
        @ColumnInfo(name = "amount_cents") public long amountCents;
        public String description;
        public String notes;
        @ColumnInfo(name = "created_at") public long createdAt;
        @ColumnInfo(name = "updated_at") public long updatedAt;

        public TransactionEntity(long id, long dateEpochDay, long categoryId,
                                 @NonNull String type, long amountCents,
                                 String description, String notes,
                                 long createdAt, long updatedAt) {
            this.id = id;
            this.dateEpochDay = dateEpochDay;
            this.categoryId = categoryId;
            this.type = type;
            this.amountCents = amountCents;
            this.description = description;
            this.notes = notes;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    @Entity(
            tableName = "budgets",
            foreignKeys = @ForeignKey(
                    entity = CategoryEntity.class,
                    parentColumns = "id",
                    childColumns = "category_id",
                    onDelete = ForeignKey.RESTRICT
            ),
            indices = {
                    @Index("category_id"),
                    @Index(value = {"month_key", "category_id"}, unique = true)
            }
    )
    public static class BudgetEntity {
        @PrimaryKey(autoGenerate = true)
        public long id;
        @NonNull @ColumnInfo(name = "month_key") public String monthKey;
        @ColumnInfo(name = "category_id") public long categoryId;
        @ColumnInfo(name = "desired_cap_cents") public Long desiredCapCents;
        @ColumnInfo(name = "funded_cents") public long fundedCents;
        @NonNull @ColumnInfo(name = "rule_type") public String ruleType;
        @ColumnInfo(name = "rate_basis_points") public Integer rateBasisPoints;

        public BudgetEntity(long id, @NonNull String monthKey, long categoryId,
                            Long desiredCapCents, long fundedCents,
                            @NonNull String ruleType, Integer rateBasisPoints) {
            this.id = id;
            this.monthKey = monthKey;
            this.categoryId = categoryId;
            this.desiredCapCents = desiredCapCents;
            this.fundedCents = fundedCents;
            this.ruleType = ruleType;
            this.rateBasisPoints = rateBasisPoints;
        }
    }

    @Entity(tableName = "goals")
    public static class GoalEntity {
        @PrimaryKey(autoGenerate = true)
        public long id;
        @NonNull public String name;
        @ColumnInfo(name = "target_cents") public long targetCents;
        @NonNull @ColumnInfo(name = "color_key") public String colorKey;
        @ColumnInfo(name = "is_active") public boolean isActive;
        @ColumnInfo(name = "sort_order") public int sortOrder;

        public GoalEntity(long id, @NonNull String name, long targetCents,
                          @NonNull String colorKey, boolean isActive, int sortOrder) {
            this.id = id;
            this.name = name;
            this.targetCents = targetCents;
            this.colorKey = colorKey;
            this.isActive = isActive;
            this.sortOrder = sortOrder;
        }
    }

    @Entity(
            tableName = "goal_entries",
            foreignKeys = @ForeignKey(
                    entity = GoalEntity.class,
                    parentColumns = "id",
                    childColumns = "goal_id",
                    onDelete = ForeignKey.CASCADE
            ),
            indices = {@Index("goal_id"), @Index("date_epoch_day")}
    )
    public static class GoalEntryEntity {
        @PrimaryKey(autoGenerate = true)
        public long id;
        @ColumnInfo(name = "goal_id") public long goalId;
        @ColumnInfo(name = "date_epoch_day") public long dateEpochDay;
        @ColumnInfo(name = "amount_cents_signed") public long amountCentsSigned;
        public String notes;
        @ColumnInfo(name = "created_at") public long createdAt;

        public GoalEntryEntity(long id, long goalId, long dateEpochDay,
                               long amountCentsSigned, String notes, long createdAt) {
            this.id = id;
            this.goalId = goalId;
            this.dateEpochDay = dateEpochDay;
            this.amountCentsSigned = amountCentsSigned;
            this.notes = notes;
            this.createdAt = createdAt;
        }
    }

    @Dao
    public interface CategoryDao {
        @Query("SELECT * FROM categories ORDER BY sort_order, name")
        List<CategoryEntity> getAll();

        @Query("SELECT COUNT(*) FROM categories")
        int count();

        @Insert
        long insert(CategoryEntity category);

        @Insert
        List<Long> insertAll(List<CategoryEntity> categories);

        @Update
        void update(CategoryEntity category);

        @Query("DELETE FROM categories")
        void deleteAll();
    }

    @Dao
    public interface TransactionDao {
        @Query("SELECT * FROM transactions ORDER BY date_epoch_day DESC, created_at DESC")
        List<TransactionEntity> getAll();

        @Insert
        long insert(TransactionEntity transaction);

        @Insert
        List<Long> insertAll(List<TransactionEntity> transactions);

        @Update
        void update(TransactionEntity transaction);

        @Query("DELETE FROM transactions WHERE id = :id")
        void deleteById(long id);

        @Query("DELETE FROM transactions")
        void deleteAll();
    }

    @Dao
    public interface BudgetDao {
        @Query("SELECT * FROM budgets ORDER BY month_key, id")
        List<BudgetEntity> getAll();

        @Query("SELECT * FROM budgets WHERE month_key = :monthKey ORDER BY id")
        List<BudgetEntity> getForMonth(String monthKey);

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        long insert(BudgetEntity budget);

        @Insert
        List<Long> insertAll(List<BudgetEntity> budgets);

        @Query("DELETE FROM budgets")
        void deleteAll();
    }

    @Dao
    public interface GoalDao {
        @Query("SELECT * FROM goals ORDER BY sort_order, name")
        List<GoalEntity> getAll();

        @Query("SELECT COUNT(*) FROM goals")
        int count();

        @Insert
        long insert(GoalEntity goal);

        @Insert
        List<Long> insertAll(List<GoalEntity> goals);

        @Update
        void update(GoalEntity goal);

        @Query("DELETE FROM goals")
        void deleteAll();
    }

    @Dao
    public interface GoalEntryDao {
        @Query("SELECT * FROM goal_entries ORDER BY date_epoch_day DESC, created_at DESC")
        List<GoalEntryEntity> getAll();

        @Insert
        long insert(GoalEntryEntity entry);

        @Insert
        List<Long> insertAll(List<GoalEntryEntity> entries);

        @Query("DELETE FROM goal_entries")
        void deleteAll();
    }
}
