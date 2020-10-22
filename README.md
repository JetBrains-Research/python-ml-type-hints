# python-ml-type-hints

## DeepTyper

Work and training time:  
  Work time -- less than 2 seconds for average sized files (taken from the article, not tested)
  Training time -- not mentioned in the article

Gist -- two bi-RNN connected by so called "consistency layers" considering a possibility that identifier appears several times within one code snippet (although it does not consider that it could be appearence of different identifiers). It is done with averaging of output of RNN for each appearence of each identifer within one code snippet. 
Noticed problems:  
  - Model shows weak improvement (for top-1 and top-5 accuracy) against plain RNN according to results -- top-1 accuracy is 2% higher, whereas top-5 accuracy is the same.  
  - Problems connected with previous -- there is a comparison of advanced model with other type inference tools, but the article lacks comparison of plain RNN with such tools (or comparison of advanced RNN with advanced model)   
  - No structural information is considered, code is comprehended as sequence of tokens. This design decision is a trade-off between reusage of existing model and productivity.
  - Closed-world type suggestion offers no opportunity to add new types to model without retraining it

Data:  
  - Top-1000 most starred GitHub projects written mostly in TypeScript, excluding projects with more than 5000 tokens and projects, containing only TypeScript header files, only 776 projects left. Each project wath parsed with ```tsc``` compiler, which infers type information for each identifier (possibly ```any```).
  
Comparison:  
  - Naive method, which assigns each identifier the type distribution it has at a training time.
  - Plain RNN -- RNN without consistency layers, shows almost same results, has problems with consistency -- it is possible that plain RNN offers different types for different occurences of one identifier
  - tsc+CheckJS -- comparison against types that ```tsc``` could infer when also equipped with static type-inference tool CheckJS. Hybrid model, which applies DeepTyper to the results of type inference conducted by tsc+CheckJS. DeepTyper shows significant accuracy boost against CheckJS and hybrid model.
  - JSNice -- tool for type-inference in JS functions using statistic from dependency graphsl learned on a large corpus. Since it is only available to use via a web form, only 30 experiments were held (30 random functions were taken from top-100 most starred JS projects on GitHub, for each function correct types were manually determined). In a same manner with CheckJS hybrid model was invented. With confidence threshold set at 90% accuracy reaches 65%, whereas there are only 2% incorrect or partially correct results, (for 167 identifiers, which needed type-inference). It is 10% more, compared to JSNice and DeepTyper с with 0% threshold, whereas it shows a same amount or even less incorrect results.
  

Main pros of DeepTyper -- consistency, indifference to structural information about source code (both pro and flaw -- DeepTyper can be applied to many languages but it does not take in account properties of a specific language)


## TypeWriter
Gist -- several RNN's for tokens, identifiers and comments, modified by feedback-directed search and a static type checker. Top-k types inferred by model are passed to a tool searching for consistent types. That is done in a following manner:  
  1. Assume most prediction by TypeWriter is correct
  2. Try to set all unknown (not annotated types) to top-1 prediction by TypeWriter
  3. Run static type-checker on new code
  4. Try to minimize feedback function (weighted sum of yet unpredicted annotations and type errors caused by incorrect predictions) by changing some predictions to other candidates
  5. End or go to 3.
  
Model predicts function arguments types and function return type separately.  

To annotate function arguments it uses such parameters as:  
  - function name
  - argument name
  - all other arguments names
  - all usages of argument
  - argument type  
  
To annotate function return type it uses such parameters as:
  - function name
  - sequence of argument names
  - comment assosiated with function
  - set of return statements
  - return type
Noticed problems:  
 - Closed-world type suggestion
 - Feedback-directed search is slow
 - Structural information is not considered (except some, like usage sentences and return statements)

Data:  
  - Internal Facebook code base
  - `python3` tagged GitHub projects with more than 50 stars, all Python project with `mypy` as dependency on Library.io, total 1137 repos and app. 12000 files, where 55% of functions have annotated return types and 41% of arguments have type annotations  

Comparison:
  - Naive model -- considers top-10 most frequent types in dataset and samples its prediction from the distribution of these 10 types
  - DeepTyper -- reimplementation of DeepTyper project for Python
  - NL2Type -- reimplementation of NL2Type project for Python  
TypeWriter outperforms all three presented models both in arguments type prediction task and return type prediction task by app. 10-15% for top-1 precision, and shows somewhat similar result to DeepTyper and NL2Type for top-3 and top-5 precision, and the same can be applied to recall.
  - pyre infer -- static type inference tool. Comparison was conducted on a set of randomly chosen, fully annotated files from the industrial code base at Facebook. TypeWriter outperforms pyre infer, suggesting much more types than static analysis, and in most cases where both tools suggest a type, they suggest identical type.

Pros of TypeWriter:  
  - code passed through TypeWriter does not contain type errors if it did not contain any
  - TypeWriter considers task of predicting function return type and function arguments type as different tasks, thus solving each task more precisely
  - TypeWriter captures some structural information about source code, like usage statements and return statements, unlike DeepTyper

## Typilus
Gist -- GNN with several types of edges adapted to Python syntaxis and nodes that uses deep similarity learning and KNN to tackle the open-world type suggestion problem, also uses type checker to deal with false positive guesses.

Working time:  
  - single training epoch takes 86sec on a Nvidia K80 GPU
  - single inference epoch takes 7.3sec

Noticed problems:  
 - Yet to investigate
 
 Data: 
  - 600 Python Github repositories that contain at least one type annotation
  - `pytype` is run to augment corpus with type annotations that can be inferred from a static analysis tool
  - deduplication tool is run, removing code duplicates to exclude bias from results
  - around 25k non-Any types, most popular are `str`, `int` and `bool`
  - top-10 types account for more than half of the dataset, however, types, used less than 100 times, still account for 32% of the dataset

Comparison:  
  - DeepTyper based models -- reimplementation of DeepTyper project for python, using subtokens instead of full tokens and consistency module added to output biGRU layer
  - code2seq based models  

In all experiments all models except Typilus trains with different way of training. There are 3 options -- the classification-based loss that narrows the problem to closed-world type suggestion, deep similarity learning that produces a type space and `Typilus loss` that combines both.  
Typilus outperforms all presented models, reaching 54.6% accuracy when considering exact type match with 77.2% accuracy on common types (types used at least 100 times in the dataset) and 22.5% accuracy on rare types (all other types), and reaching 64.1% accuracy when considering match up to parametric type (match when ignoring type parameters) with 80.3% accuracy on common types and 41.2% accuracy on rare types.

Pros of Typilus:  
  - open-world type suggestion
  - type checker to deal with false positives guesses
  - GNN to capture structural data
