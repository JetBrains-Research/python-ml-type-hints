import pandas as pd
import numpy as np
from tqdm import tqdm

PATH_TO_TRUE_ANNOTATIONS_PYCHARM =
PATH_TO_PREDICTIONS_PYCHARM =
PATH_TO_TRUE_ANNOTATIONS_MODEL =
PATH_TO_PREDICTIONS_MODEL =
PATH_TO_RESULT =

df_true = pd.read_csv(PATH_TO_TRUE_ANNOTATIONS_PYCHARM, sep=";")
df_pred = pd.read_csv(PATH_TO_PREDICTIONS_PYCHARM, sep=";")

df_true = df_true[(df_true['type'] != 'Any')]

df_true_dltpy = pd.read_csv(PATH_TO_TRUE_ANNOTATIONS_MODEL, sep=";")
df_pred_dltpy = pd.read_csv(PATH_TO_PREDICTIONS_MODEL, sep=";")

match: pd.DataFrame = df_true.merge(df_pred, on=['file', 'lineno', 'name', 'element'], copy=False)

# match = match[(match['name'] != 'self')]

functions_pycharm_match = match[(match['element'] == 'FUNCTION')]
parameters_pycharm_match = match[(match['element'] == 'PARAMETER')]

match_dltpy: pd.DataFrame = df_true_dltpy.merge(df_pred_dltpy, on=['file', 'lineno', 'name'], copy=False)

df_pycharm_all = match[(match['type_x'] == match['type_y']) & (match['type_x'] != 'Any')]
pycharm_functions = functions_pycharm_match[(functions_pycharm_match['type_x'] == functions_pycharm_match['type_y']) & (functions_pycharm_match['type_x'] != 'Any')]
pycharm_parameters = parameters_pycharm_match[(parameters_pycharm_match['type_x'] == parameters_pycharm_match['type_y']) & (parameters_pycharm_match['type_x'] != 'Any')]

df_dltpy = match_dltpy[(match_dltpy['type_x'] == match_dltpy['type_y'])]

print(len(df_pycharm_all.index) / len(match.index))

print(len(df_pycharm_all.index))

print(len(match_dltpy.index))

i = 0
for index, row in tqdm(match_dltpy.iterrows()):
    i += 1
    lineno_dltpy = row['lineno']
    linenos = df_pred[(df_pred['file'] == row['file']) & (df_pred['name'] == row['name'])]['lineno'].to_numpy()
    try:
        num = linenos[(np.abs(linenos - lineno_dltpy)).argmin()]
        match_dltpy.loc[index, 'lineno'] = num
    except Exception:
        continue
     #print(i / len(df_dltpy.index))
df_all = match_dltpy.merge(df_pred, on=['file', 'lineno', 'name'], copy=False)



df_all.to_csv(PATH_TO_RESULT)


df_all = pd.read_csv(PATH_TO_RESULT)

df_true_py = df_all[(df_all['type_x'] == df_all['type'])]
df_dltpy_py = df_all[(df_all['type_y'] == df_all['type'])]
df_true_dltpy_ = df_all[df_all['type_x'] == df_all['type_y']]
print(len(df_true_py.index), len(df_dltpy_py.index), len(df_true_dltpy_.index), len(df_all.index))
